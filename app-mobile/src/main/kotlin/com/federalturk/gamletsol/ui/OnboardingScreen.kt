package com.federalturk.gamletsol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * İlk açılış tanıtım/kurulum akışı (3 sayfa). Kullanıcı geri bildirimi:
 * önceki beta sürümünde izin verildikten sonra ekranda hiçbir menü/
 * kurulum talimatı yoktu. Bu ekran, kalıcı kamera izni istemeden ÖNCE
 * gösterilir ve `OnboardingPrefs.onboardingComplete = true` olduktan
 * sonra bir daha otomatik açılmaz (üst menüden "Tanıtımı yeniden göster"
 * ile tekrar çağrılabilir).
 *
 * NOT (dürüstlük ilkesi): Bu, yol haritasındaki G037 ("Onboarding ve
 * oturum ekranları", P5) için planlanan tam akışın erken/basitleştirilmiş
 * bir sürümüdür — gerçek cihazda ilk-kez-kullanan biriyle HENÜZ
 * denenmedi; metinlerin/adımların yeterli olup olmadığı doğrulanmadı.
 */
private data class OnboardingPage(
    val title: String,
    val body: String,
)

private val pages = listOf(
    OnboardingPage(
        title = "GAMLETSOL'a hoş geldiniz",
        body = "GAMLETSOL, oyun/film/dizi ekranında zaten görünen İngilizce " +
            "altyazıyı kameranla okur ve Türkçeye çevirir — ekranda konuşma " +
            "veya ses DİNLEMEZ, yalnızca görünen yazıyı okur (OCR).",
    ),
    OnboardingPage(
        title = "Nasıl çalışır ve gizlilik",
        body = "Metin tanıma (OCR) ve çeviri tamamen CİHAZ ÜZERİNDE yapılır " +
            "(Google ML Kit). Kamera görüntüsü hiçbir zaman bir sunucuya " +
            "yüklenmez. Çeviri hem ekranda yazı olarak gösterilir hem de " +
            "Türkçe sesli okunur.\n\nBu bir BETA sürümdür: OCR/çeviri " +
            "kalitesi her koşulda mükemmel olmayabilir, ayar menüsü ve " +
            "oyun profilleri henüz eklenmedi.",
    ),
    OnboardingPage(
        title = "Kurulum — başlamadan önce",
        body = "1. Oyun/cihaz ayarlarından altyazıları (Subtitles/Captions) " +
            "İNGİLİZCE olarak açın.\n" +
            "2. Telefonu, altyazının göründüğü ekranı (TV/monitör) net ve " +
            "sabit görecek şekilde tutun — mümkünse bir stand/tripod kullanın.\n" +
            "3. Az sonra istenecek kamera iznine \"İzin Ver\" deyin.\n" +
            "4. İlk açılışta çeviri modeli indirilir — buna Wi-Fi gerekir.\n" +
            "5. Altyazı net göründüğünde çeviri birkaç kare içinde ekranın " +
            "altında belirir ve sesli okunur.",
    ),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var pageIndex by remember { mutableIntStateOf(0) }
    val page = pages[pageIndex]
    val isLastPage = pageIndex == pages.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Yatayda yükseklik az: metin, alttaki düğmelerin üstüne taşmasın diye
        // kalan alanı kaplayan kaydırılabilir bir sütunda.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = page.title,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = page.body,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            PageDots(count = pages.size, selected = pageIndex)
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (pageIndex > 0) {
                    OutlinedButton(onClick = { pageIndex -= 1 }) {
                        Text("Geri")
                    }
                } else {
                    TextButton(onClick = onDone) {
                        Text("Atla", color = Color.White.copy(alpha = 0.6f))
                    }
                }

                Button(
                    onClick = {
                        if (isLastPage) onDone() else pageIndex += 1
                    },
                ) {
                    Text(if (isLastPage) "Başla" else "İleri")
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, selected: Int) {
    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (index == selected) 10.dp else 8.dp)
                    .background(
                        color = if (index == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White.copy(alpha = 0.3f)
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}
