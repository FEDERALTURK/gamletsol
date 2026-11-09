package com.federalturk.gamletsol.core.transport

/**
 * [TvReceiverAdapter]'ın soket tabanlı (bu modüldeki TCP [TvEventClient])
 * uygulaması — zaten bağlanmış/eşleşmiş bir istemciyi sarar. Şu an tek
 * uygulama bu; ileride (Samsung Tizen/LG tarayıcı alıcıları, roadmap
 * genişlemesi) başka `TvReceiverAdapter` uygulamaları eklenebilir, çağıran
 * kod (örn. `TvRelaySubtitleSink`) arayüze bağlı kaldığı sürece değişmez.
 */
class SocketTvReceiverAdapter(private val client: TvEventClient) : TvReceiverAdapter {
    override val isConnected: Boolean get() = client.isConnected
    override val capabilities: TvCapabilities get() = client.capabilities

    override fun sendSubtitle(event: CueEvent): Boolean = client.send(event)

    override fun clear(cueId: String): Boolean {
        // bkz. TvReceiverAdapter.clear dokümantasyonu — protokol henüz bir
        // "temizle" mesaj türü tanımlamıyor.
        return false
    }

    override fun heartbeat(): Boolean = client.isConnected

    override fun disconnect() = client.close()
}
