package com.yunpu.yunpumusic.dev;

import com.yunpu.yunpumusic.api.QrLoginSession;
import com.yunpu.yunpumusic.api.provider.KugouProvider;
import com.yunpu.yunpumusic.util.QrCode;

/** Live smoke check for Kugou QR creation and the first, unauthenticated poll. */
public final class DevKugouQrProbe {
    public static void main(String[] args) throws Exception {
        KugouProvider provider = new KugouProvider();
        QrLoginSession session = provider.startLogin();
        if (session.status() != QrLoginSession.Status.WAITING
            || session.sessionKey().isEmpty()
            || !session.qrContent().startsWith(
                    "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=1005&qrcode=")) {
            throw new IllegalStateException("Kugou QR creation failed: " + session.message());
        }
        QrCode qr = QrCode.encode(session.qrContent(), QrCode.Ecc.M);
        if ((qr.getSize() + 4) * 2 > 102) {
            throw new IllegalStateException("Kugou QR is too dense for the current screen area");
        }
        session = provider.pollLogin(session);
        if (session.status() != QrLoginSession.Status.WAITING) {
            throw new IllegalStateException("Kugou initial poll failed: " + session.message());
        }
        System.out.println("Kugou QR creation and first poll succeeded; login still requires a real app scan.");
    }
}
