package com.federalturk.gamletsol.ui

import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.federalturk.gamletsol.CaptionViewModel
import com.federalturk.gamletsol.PipelineStatus
import com.federalturk.gamletsol.TvConnectionState
import com.federalturk.gamletsol.core.pairing.TrustedTv
import com.federalturk.gamletsol.core.transport.DiscoveredTv
import com.federalturk.gamletsol.tv.TrustedTvStore
import com.federalturk.gamletsol.tv.TvNsdDiscoverer
import androidx.camera.view.PreviewView

/**
 * NOT (geri bildirim, 2026-11-09): İlk beta sürümünde izin verildikten
 * sonra ekranda kamera önizlemesi dışında HİÇBİR şey yoktu — ne başlık, ne
 * durum bilgisi, ne de bir menü vardı. Sonra bir bilgi (i) düğmesi
 * eklendi; bu sürümde kullanıcı "ilk açılışta menüler, kurulum talimatları
 * ve diğer bilgiler olsun" diye tekrar istedi, bu yüzden:
 * - İlk açılışta ayrı bir [OnboardingScreen] akışı gösteriliyor
 *   (MainActivity'de yönlendiriliyor, bkz. OnboardingPrefs).
 * - Ana ekrandaki tek "bilgi" düğmesi, birden çok öğeli gerçek bir
 *   AÇILIR MENÜYE (overflow) dönüştürüldü: Kurulum Talimatları, Gizlilik,
 *   Tanıtımı Yeniden Göster.
 *
 * NOT (2026-11-09, kullanıcı netleştirmesi): Kullanıcının asıl istediği
 * özellik telefon↔TV bağlantısı kurup çeviriyi TV'ye yansıtmaktı — bu
 * yüzden menüye bir "TV'ye Yansıt" öğesi eklendi (bkz. [TvConnectDialog]),
 * P3/G022-G030'un ilk/MVP sürümü (core-transport modülü).
 *
 * NOT (2026-11-09, G023 QR tamamlandı): [scanningQr] `true` olduğunda, ana
 * OCR kamerası ([CameraPreview]) composition'dan TAMAMEN kaldırılıp yerine
 * [QrScannerScreen] gösterilir — ikisi AYNI ANDA bağlı DEĞİLDİR (tek bir
 * `ProcessCameraProvider` örneği var, ikisi de kendi `bindToLifecycle`
 * çağrısında `unbindAll()` yapıyor). `scanningQr` tekrar `false` olduğunda
 * `CameraPreview` composition'a YENİDEN girer (View'ı AndroidView tarafından
 * yeniden oluşturulur), bu da OCR kamerasını otomatik olarak yeniden bağlar.
 */
@Composable
fun CaptionScreen(
    viewModel: CaptionViewModel,
    onRequestCameraPermission: () -> Unit,
    onShowOnboardingAgain: () -> Unit,
) {
    val granted by viewModel.cameraPermissionGranted.collectAsState()
    val cue by viewModel.subtitleSink.activeCue.collectAsState()
    val status by viewModel.pipelineStatus.collectAsState()
    val tvConnectionState by viewModel.tvConnectionState.collectAsState()
    // G032: Türkçe ses kurulu değilse veya ağ gerektiriyorsa kısa bir uyarı —
    // `null` ise (ses normal/çevrimdışı çalışıyor ya da motor henüz
    // başlatılıyor) hiçbir şey gösterilmez (bkz. CaptionViewModel KDoc'u).
    val turkishVoiceStatusMessage by viewModel.turkishVoiceStatusMessage.collectAsState()
    var scanningQr by remember { mutableStateOf(false) }

    // G025: NSD/mDNS taraması — ekran açık olduğu sürece arka planda
    // çalışır (QR tarama/kamera ile ÇAKIŞMAZ, NsdManager kamerayı
    // kullanmaz). Bulunan TV'ler "TV'ye Yansıt" diyaloğunda listelenir;
    // hiçbiri bulunmazsa (NSD engellenmiş/desteklenmiyor) liste boş kalır
    // ve kullanıcı QR/manuel girişe geri döner — bu bir hata DEĞİLDİR.
    val context = LocalContext.current
    val nsdDiscoverer = remember { TvNsdDiscoverer(context) }
    var discoveredTvs by remember { mutableStateOf<List<DiscoveredTv>>(emptyList()) }
    DisposableEffect(Unit) {
        nsdDiscoverer.startDiscovery(
            onDiscovered = { tv ->
                discoveredTvs = discoveredTvs.filterNot { it.serviceName == tv.serviceName } + tv
            },
            onLost = { name -> discoveredTvs = discoveredTvs.filterNot { it.serviceName == name } },
        )
        onDispose { nsdDiscoverer.stopDiscovery() }
    }

    // G029 ("eşleştirmeyi hatırlama ve iptal"): kalıcı olarak hatırlanan
    // TV'ler — ekran ilk açıldığında SharedPreferences'tan yüklenir (bkz.
    // [TrustedTvStore]). `rememberNewTv` açıkken (varsayılan AÇIK), QR
    // tarama ya da NSD ile bulunan bir TV'ye bağlanıldığında (ikisi de
    // GERÇEK bir fingerprint taşır) o TV otomatik olarak hatırlanır —
    // kullanıcı "Bağlan"a basarak zaten bu güveni açıkça seçmiş olur;
    // istemezse düğmeyi kapatabilir ya da sonradan "Unut"a basabilir.
    // Manuel giriş yolu fingerprint TAŞIMADIĞI için hiç hatırlanamaz
    // (bkz. TrustedTv dokümantasyonu).
    var trustedTvs by remember { mutableStateOf(TrustedTvStore.load(context)) }
    var rememberNewTv by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (scanningQr) {
            QrScannerScreen(
                onResult = { payload ->
                    scanningQr = false
                    // G024: QR, TV'nin TLS fingerprint'ini de taşır — bu yolla
                    // bağlanan istemci gerçek MITM korumalı pinning kullanır
                    // (manuel giriş yolundan farklı olarak, bkz. aşağıdaki
                    // onConnectToTv sarmalayıcısı).
                    viewModel.connectToTv(payload.host, payload.port, payload.code, payload.fingerprintSha256)
                    if (rememberNewTv) {
                        trustedTvs = TrustedTvStore.remember(
                            context,
                            TrustedTv(
                                displayName = "TV (${payload.host})",
                                host = payload.host,
                                port = payload.port,
                                fingerprintSha256 = payload.fingerprintSha256,
                                rememberedAtMillis = System.currentTimeMillis(),
                            ),
                        )
                    }
                },
                onCancel = { scanningQr = false },
            )
        } else {
            if (granted) {
                CameraPreview(viewModel = viewModel)
            } else {
                PermissionRequestContent(onRequestCameraPermission = onRequestCameraPermission)
            }

            TopBar(
                onShowOnboardingAgain = onShowOnboardingAgain,
                tvConnectionState = tvConnectionState,
                cameraPermissionGranted = granted,
                // G029: artık bir [expectedFingerprint] de alır — manuel
                // giriş yolunda `null` (kimlik pinlenmez, bkz. aşağıdaki
                // dürüstlük notu), hatırlanan bir TV'ye yeniden bağlanırken
                // ise o TV'nin kayıtlı fingerprint'i (gerçek MITM koruması
                // devam eder, "hatırlama" güvenliği AZALTMAZ).
                onConnectToTv = { host, port, code, expectedFingerprint ->
                    viewModel.connectToTv(host, port, code, fingerprintSha256 = expectedFingerprint)
                },
                onDisconnectFromTv = viewModel::disconnectFromTv,
                onStartQrScan = { scanningQr = true },
                discoveredTvs = discoveredTvs,
                // G025: NSD ile bulunan bir TV, fingerprint'i de TXT
                // kaydından taşıdığı için QR ile aynı güvenlik seviyesinde
                // bağlanır (gerçek MITM korumalı pinning).
                onConnectDiscovered = { tv ->
                    viewModel.connectToTv(tv.host, tv.port, tv.pairingCode, tv.fingerprintSha256)
                    if (rememberNewTv) {
                        trustedTvs = TrustedTvStore.remember(
                            context,
                            TrustedTv(
                                displayName = tv.serviceName,
                                host = tv.host,
                                port = tv.port,
                                fingerprintSha256 = tv.fingerprintSha256,
                                rememberedAtMillis = System.currentTimeMillis(),
                            ),
                        )
                    }
                },
                // G029: hatırlanan TV'ler listesi + hatırlama/unutma eylemleri.
                trustedTvs = trustedTvs,
                rememberNewTv = rememberNewTv,
                onRememberNewTvChanged = { rememberNewTv = it },
                onForgetTrustedTv = { fingerprint -> trustedTvs = TrustedTvStore.forget(context, fingerprint) },
            )

            val translated = cue?.translatedText
            if (granted && !translated.isNullOrBlank()) {
                SubtitleOverlay(original = cue?.originalText, translated = translated)
            } else if (granted) {
                StatusChip(status = status, voiceStatusMessage = turkishVoiceStatusMessage)
            }
        }
    }
}

@Composable
private fun BoxScope.TopBar(
    onShowOnboardingAgain: () -> Unit,
    tvConnectionState: TvConnectionState,
    cameraPermissionGranted: Boolean,
    onConnectToTv: (String, Int, String, String?) -> Unit,
    onDisconnectFromTv: () -> Unit,
    onStartQrScan: () -> Unit,
    discoveredTvs: List<DiscoveredTv>,
    onConnectDiscovered: (DiscoveredTv) -> Unit,
    trustedTvs: List<TrustedTv>,
    rememberNewTv: Boolean,
    onRememberNewTvChanged: (Boolean) -> Unit,
    onForgetTrustedTv: (String) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<MenuDialog?>(null) }

    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "GAMLETSOL",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Menü", tint = Color.White)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (tvConnectionState == TvConnectionState.CONNECTED) {
                                "TV bağlantısı (bağlı ✓)"
                            } else {
                                "TV'ye Yansıt"
                            },
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        dialog = MenuDialog.TV_CONNECT
                    },
                )
                DropdownMenuItem(
                    text = { Text("Kurulum talimatları") },
                    onClick = {
                        menuExpanded = false
                        dialog = MenuDialog.SETUP
                    },
                )
                DropdownMenuItem(
                    text = { Text("Gizlilik ve nasıl çalışır") },
                    onClick = {
                        menuExpanded = false
                        dialog = MenuDialog.PRIVACY
                    },
                )
                DropdownMenuItem(
                    text = { Text("Tanıtımı yeniden göster") },
                    onClick = {
                        menuExpanded = false
                        onShowOnboardingAgain()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Hakkında / yol haritası durumu") },
                    onClick = {
                        menuExpanded = false
                        dialog = MenuDialog.ABOUT
                    },
                )
            }
        }
    }

    when (dialog) {
        MenuDialog.TV_CONNECT -> TvConnectDialog(
            connectionState = tvConnectionState,
            cameraPermissionGranted = cameraPermissionGranted,
            onConnect = onConnectToTv,
            onDisconnect = onDisconnectFromTv,
            onDismiss = { dialog = null },
            onStartQrScan = {
                dialog = null
                onStartQrScan()
            },
            discoveredTvs = discoveredTvs,
            onConnectDiscovered = {
                dialog = null
                onConnectDiscovered(it)
            },
            trustedTvs = trustedTvs,
            rememberNewTv = rememberNewTv,
            onRememberNewTvChanged = onRememberNewTvChanged,
            onForgetTrustedTv = onForgetTrustedTv,
        )
        MenuDialog.SETUP -> InfoDialog(
            title = "Kurulum talimatları",
            body = "1. Oyun/cihaz ayarlarından altyazıları (Subtitles/Captions) " +
                "İNGİLİZCE olarak açın.\n" +
                "2. Telefonu, altyazının göründüğü ekranı net ve sabit görecek " +
                "şekilde tutun — mümkünse bir stand/tripod kullanın.\n" +
                "3. Altyazı net göründüğünde çeviri birkaç kare içinde ekranın " +
                "altında belirir ve sesli okunur.\n" +
                "4. İlk açılışta çeviri modeli indirilir — buna Wi-Fi gerekir.",
            onDismiss = { dialog = null },
        )
        MenuDialog.PRIVACY -> InfoDialog(
            title = "Gizlilik ve nasıl çalışır",
            body = "Metin tanıma (OCR) ve çeviri tamamen CİHAZ ÜZERİNDE yapılır " +
                "(Google ML Kit). Kamera görüntüsü hiçbir zaman bir sunucuya " +
                "yüklenmez — yalnızca ekrandaki yazı okunur, ses dinlenmez.",
            onDismiss = { dialog = null },
        )
        MenuDialog.ABOUT -> InfoDialog(
            title = "Hakkında",
            body = "GAMLETSOL — BETA sürüm. Bu, temel kamera → OCR → çeviri → " +
                "altyazı/TTS akışının çalıştığı ilk sürümdür. Ayar menüsü, " +
                "oyun profilleri, TV'ye yansıtma ve konsol bağlantısı yol " +
                "haritasındaki sonraki fazlarda (bkz. proje deposu " +
                "docs/roadmap/) planlanmıştır; henüz eklenmedi.",
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

private enum class MenuDialog { TV_CONNECT, SETUP, PRIVACY, ABOUT }

/**
 * "TV'ye Yansıt" diyaloğu — üç bağlanma yolu sunar: (1) G025'teki NSD/mDNS
 * otomatik keşfinin bulduğu TV'lerden birine dokunmak (en basit yol,
 * fingerprint de otomatik taşınır), (2) app-tv ekranındaki QR'ı kamerayla
 * taramak (G023, fingerprint de dahil — G024), (3) IP/port/eşleştirme
 * kodunu elle girmek (TV'nin kimliği pinlenmez, kanal yine de TLS ile
 * şifrelidir). Manuel giriş KALDIRILMADI — NSD'nin engellendiği/
 * desteklenmediği ağlar ve QR'ın okunamadığı durumlar için gereken yedek
 * yol olarak kalıyor. Bağlı olduğunda bağlantıyı kesme seçeneği de burada
 * gösterilir.
 *
 * G029 (2026-11-09, "eşleştirmeyi hatırlama ve iptal"): Bir "Hatırlanan
 * TV'ler" bölümü eklendi — QR/NSD ile (GERÇEK fingerprint taşıyan
 * yollar) bağlanılan TV'ler, "Yeni TV'leri hatırla" anahtarı açıkken
 * otomatik hatırlanır. Hatırlanan bir TV'ye "Bağlan" denildiğinde, o
 * TV'nin kayıtlı host/port alanları doldurulur VE kayıtlı fingerprint'i
 * [pinnedFingerprint] olarak saklanır — kullanıcı yalnızca TV ekranındaki
 * GÜNCEL (yeni, tek kullanımlık) eşleştirme kodunu girip "Bağlan"a basar;
 * bağlantı yine GERÇEK MITM korumalı pinning ile kurulur (manuel giriş
 * gibi "kimliksiz" DEĞİLDİR). Her kayıt yanında bir "Unut" düğmesi vardır.
 */
@Composable
private fun TvConnectDialog(
    connectionState: TvConnectionState,
    cameraPermissionGranted: Boolean,
    onConnect: (String, Int, String, String?) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    onStartQrScan: () -> Unit,
    discoveredTvs: List<DiscoveredTv>,
    onConnectDiscovered: (DiscoveredTv) -> Unit,
    trustedTvs: List<TrustedTv>,
    rememberNewTv: Boolean,
    onRememberNewTvChanged: (Boolean) -> Unit,
    onForgetTrustedTv: (String) -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("48631") }
    var pairingCode by remember { mutableStateOf("") }
    // G029: bir "hatırlanan TV"ye "Bağlan" denildiğinde bu, o kaydın
    // fingerprint'ine ayarlanır — manuel alanlar elle değiştirilirse
    // (kullanıcı farklı bir TV'ye bağlanmaya karar verirse) sıfırlanır,
    // çünkü artık doldurulan host/port o kayda ait OLMAYABİLİR.
    var pinnedFingerprint by remember { mutableStateOf<String?>(null) }
    // (2026-11-10, kullanıcı geri bildirimi: "eşleştirme/bağlanma süreci
    // karışık") Manuel IP/port/kod girişi artık varsayılan olarak
    // GİZLİ — bu, QR/otomatik-keşif bulunamadığı durumlar için bir yedek
    // yoldur, birincil akış değildir. Hiçbir alan/işlev kaldırılmadı.
    var showManualEntry by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("TV'ye Yansıt (beta)") },
        text = {
            Column {
                if (connectionState != TvConnectionState.CONNECTED) {
                    // Birincil akış, tam olarak Game Caption'daki gibi: TV
                    // ekranındaki QR'ı tarayıp bağlan. Tek, büyük düğme.
                    Text(
                        "TV ekranındaki QR kodunu tarayın. Telefon ve TV AYNI " +
                            "Wi-Fi ağında olmalı.",
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onStartQrScan,
                        enabled = cameraPermissionGranted,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("QR ile tara")
                    }
                    if (!cameraPermissionGranted) {
                        Text(
                            "QR taramak için önce kamera iznini verin (ana ekrandan).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (trustedTvs.isNotEmpty() || discoveredTvs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    if (trustedTvs.isNotEmpty()) {
                        Text("Hatırlanan TV'ler:", style = MaterialTheme.typography.bodySmall)
                        trustedTvs.forEach { tv ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        host = tv.host
                                        portText = tv.port.toString()
                                        pinnedFingerprint = tv.fingerprintSha256
                                        showManualEntry = true
                                    },
                                    enabled = connectionState != TvConnectionState.CONNECTING,
                                ) {
                                    Text("${tv.displayName} (${tv.host}:${tv.port})")
                                }
                                TextButton(onClick = { onForgetTrustedTv(tv.fingerprintSha256) }) {
                                    Text("Unut")
                                }
                            }
                        }
                    }
                    if (discoveredTvs.isNotEmpty()) {
                        Text("Yakındaki TV'ler:", style = MaterialTheme.typography.bodySmall)
                        discoveredTvs.forEach { tv ->
                            Button(
                                onClick = { onConnectDiscovered(tv) },
                                enabled = connectionState != TvConnectionState.CONNECTING,
                            ) {
                                Text("${tv.serviceName} (${tv.host}:${tv.port})")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { showManualEntry = !showManualEntry }) {
                        Text(if (showManualEntry) "Elle girmeyi gizle" else "QR okunmuyor mu? Elle gir")
                    }
                }

                if (connectionState == TvConnectionState.CONNECTED || showManualEntry) {
                    if (connectionState != TvConnectionState.CONNECTED) {
                        Text(
                            "TV ekranındaki IP/port ve eşleştirme kodunu girin. Kod " +
                                "60 saniye geçerlidir — süresi geçerse TV'de \"Yeni " +
                                "eşleştirme kodu oluştur\" düğmesine basıp güncel kodu " +
                                "girin.\n\n" +
                                "NOT: Manuel girişte TV'nin kimliği doğrulanmaz (yine de " +
                                "şifrelidir); QR/otomatik keşif ile bağlanıldığında " +
                                "TV'nin kimliği de pinlenir (gerçek MITM koruması).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Yeni TV'leri hatırla:", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { onRememberNewTvChanged(!rememberNewTv) }) {
                                Text(if (rememberNewTv) "Açık" else "Kapalı")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = host,
                            onValueChange = {
                                host = it
                                // Kullanıcı hatırlanan bir kaydı doldurduktan
                                // sonra alanı elle değiştirirse, artık BAŞKA
                                // bir TV'ye bağlanmayı deniyor olabilir —
                                // pinlenmiş fingerprint o eski kayda ait
                                // kalmamalı.
                                pinnedFingerprint = null
                            },
                            label = { Text("TV IP adresi (ör. 192.168.1.23)") },
                            enabled = connectionState != TvConnectionState.CONNECTING,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = portText,
                            onValueChange = {
                                portText = it
                                pinnedFingerprint = null
                            },
                            label = { Text("Port") },
                            enabled = connectionState != TvConnectionState.CONNECTING,
                        )
                        if (pinnedFingerprint != null) {
                            Text(
                                "Bu TV hatırlanıyor — bağlantı kimliği doğrulanmış (pinlenmiş) olarak kurulacak.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4DD0E1),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pairingCode,
                            onValueChange = { pairingCode = it },
                            label = { Text("Eşleştirme kodu (TV'de gösterilen)") },
                            enabled = connectionState != TvConnectionState.CONNECTING,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    val statusText = when (connectionState) {
                        TvConnectionState.DISCONNECTED -> "Bağlı değil."
                        TvConnectionState.CONNECTING -> "Bağlanılıyor…"
                        TvConnectionState.CONNECTED -> "Bağlandı ✓ — çeviriler artık TV'ye de gönderiliyor."
                        TvConnectionState.FAILED -> "Bağlanılamadı — IP/port/eşleştirme kodunu ve aynı ağda olduğunuzu kontrol edin."
                    }
                    Text(statusText)
                }
            }
        },
        confirmButton = {
            if (connectionState == TvConnectionState.CONNECTED) {
                Button(onClick = { onDisconnect() }) { Text("Bağlantıyı kes") }
            } else {
                Button(
                    onClick = {
                        val port = portText.toIntOrNull()
                        if (host.isNotBlank() && port != null && pairingCode.isNotBlank()) {
                            onConnect(host.trim(), port, pairingCode.trim(), pinnedFingerprint)
                        }
                    },
                    enabled = connectionState != TvConnectionState.CONNECTING,
                ) { Text("Bağlan") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Kapat") }
        },
    )
}

@Composable
private fun BoxScope.StatusChip(status: PipelineStatus, voiceStatusMessage: String? = null) {
    val text = when (status) {
        PipelineStatus.STARTING -> "Başlatılıyor…"
        PipelineStatus.DOWNLOADING_TRANSLATION_MODEL -> "Çeviri modeli indiriliyor (yalnızca ilk sefer, Wi-Fi gerekir)…"
        PipelineStatus.MODEL_DOWNLOAD_FAILED -> "Çeviri modeli indirilemedi — Wi-Fi'ye bağlanıp uygulamayı yeniden açın"
        PipelineStatus.SCANNING -> "Kamerayı İngilizce altyazının göründüğü ekrana doğrultun — altyazı aranıyor…"
    }
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp)
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(12.dp),
    ) {
        Text(text = text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        // G032: Türkçe ses eksikse/ağ gerektiriyorsa ikinci, daha küçük bir
        // satır — altyazı metni YUKARIDA zaten gösterilmeye devam ediyor,
        // bu satır yalnızca ek bilgi amaçlıdır.
        if (voiceStatusMessage != null) {
            Text(
                text = voiceStatusMessage,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Tamam") }
        },
    )
}

@Composable
private fun PermissionRequestContent(onRequestCameraPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "GAMLETSOL, ekrandaki İngilizce altyazıyı okuyabilmek için " +
                "kameranı kullanır. Görüntü hiçbir zaman sunucuya yüklenmez.",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onRequestCameraPermission) {
            Text("Kamera iznini ver")
        }
    }
}

@Composable
private fun CameraPreview(viewModel: CaptionViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    viewModel.bindCamera(
                        cameraProvider = cameraProvider,
                        lifecycleOwner = lifecycleOwner,
                        preview = preview,
                    )
                },
                ContextCompat.getMainExecutor(ctx),
            )
            previewView
        },
    )
}

@Composable
private fun BoxScope.SubtitleOverlay(original: String?, translated: String) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp)
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(12.dp),
    ) {
        Text(
            text = translated,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        if (!original.isNullOrBlank()) {
            Text(
                text = original,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
