package com.kdt.mrpc;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Map;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import javax.net.ssl.SSLParameters;

public class MainActivity extends Activity 
{
    private SharedPreferences pref;

    private Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private Runnable heartbeatRunnable;
    private WebSocketClient webSocketClient;
    private Thread heartbeatThr, wsThr;
    private int heartbeat_interval, seq;
    private String authToken;

    private WebView webView;
    private TextView textviewLog;
    private Button buttonConnect, buttonSetActivity;
    private EditText editActivityName, editActivityState, editActivityDetails;
    private ImageButton imageIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        heartbeatRunnable = new Runnable(){
            @Override
            public void run() {
                try {
                    appendlnToLog("Heartbeat wait for " + heartbeat_interval);
                    if (heartbeat_interval < 10000) throw new RuntimeException("invalid");
                    Thread.sleep(heartbeat_interval);
                    webSocketClient.send(/*encodeString*/("{\"op\":1, \"d\":" + (seq==0?"null":Integer.toString(seq)) + "}"));
                    appendlnToLog("Heartbeat sent");
                } catch (InterruptedException e) {}
            }
        };

        pref = PreferenceManager.getDefaultSharedPreferences(this);

        webView = (WebView) findViewById(R.id.mainWebView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setAppCacheEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                Log.d("Web", "Attempt to enter " + url);
                webView.stopLoading();
                if (url.endsWith("/app")) {
                    webView.setVisibility(View.GONE);
                    extractToken();
                    login(v);
                }
                return false;
                // super.shouldOverrideUrlLoading(v, url);
            }
        });

        textviewLog = (TextView) findViewById(R.id.textviewLog);
        buttonConnect = (Button) findViewById(R.id.buttonConnect);
        buttonSetActivity = (Button) findViewById(R.id.buttonSetActivity);
        buttonSetActivity.setEnabled(false);
        editActivityName = (EditText) findViewById(R.id.editActivityName);
        editActivityState = (EditText) findViewById(R.id.editActivityState);
        editActivityDetails = (EditText) findViewById(R.id.editActivityDetails);
        imageIcon = (ImageButton) findViewById(R.id.imageIcon);
    }
    /*
     @Override
     protected void onResume() {
     super.onResume();
     Uri data = getIntent().getData(); 
     if (data != null && wsThr == null) {
     if (data.toString().contains("access_token=")) {
     accessToken = data.toString().substring(
     data.toString().indexOf("access_token=") + 13,
     data.toString().indexOf("&expires_in")
     );
     actualConnect(null);
     } else {
     actualConnect(data.getQueryParameter("code"));
     }
     }
     }
     */

    public void sendPresenceUpdate(View v) {
        long current = System.currentTimeMillis();

        ArrayMap<String, Object> presence = new ArrayMap<>();

        if (editActivityName.getText().toString().isEmpty()) {
            presence.put("activities", new Object[]{});
        } else {
            ArrayMap<String, Object> activity = new ArrayMap<>();
            activity.put("name", editActivityName.getText().toString());
            if (!editActivityState.getText().toString().isEmpty()) {
                activity.put("state", editActivityState.getText().toString());
            }
            if (!editActivityDetails.getText().toString().isEmpty()) {
                activity.put("details", editActivityDetails.getText().toString());
            }
            activity.put("type", 0);
            
            // activity.put("application_id", "567994086452363286");
            ArrayMap<String, Object> button = new ArrayMap<>();
            button.put("label", "Open GitHub");
            button.put("url", "https://github.com");
            // activity.put("buttons", new Object[]{button});

            ArrayMap<String, Object> timestamps = new ArrayMap<>();
            timestamps.put("start", current);

            activity.put("timestamps", timestamps);
            presence.put("activities", new Object[]{activity});
        }

        presence.put("afk", true);
        presence.put("since", current);
        presence.put("status", null);

        ArrayMap<String, Object> arr = new ArrayMap<>();
        arr.put("op", 3);
        arr.put("d", presence);

        webSocketClient.send(gson.toJson(arr));
    }

    private void sendIdentify() {
        ArrayMap<String, Object> prop = new ArrayMap<>();
        prop.put("$os", "linux");
        prop.put("$browser", "Discord Android");
        prop.put("$device", "unknown");

        ArrayMap<String, Object> data = new ArrayMap<>();
        data.put("token", authToken);
        data.put("properties", prop);
        data.put("compress", false);
        data.put("intents", 0);

        ArrayMap<String, Object> arr = new ArrayMap<>();
        arr.put("op", 2);
        arr.put("d", data);

        webSocketClient.send(gson.toJson(arr));
    }

    private void createWebSocketClient() {
        appendlnToLog("Connecting...");

        URI uri;
        try {
            uri = new URI("wss://gateway.discord.gg/?encoding=json&v=9"); // &compress=zlib-stream");
        }
        catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        ArrayMap<String, String> headerMap = new ArrayMap<>();
        //headerMap.put("Accept-Encoding", "gzip");
        //headerMap.put("Content-Type", "gzip");

        webSocketClient = new WebSocketClient(uri, headerMap) {
            @Override
            public void onOpen(ServerHandshake s) {
                appendlnToLog("Connection opened");
            }

            @Override
            public void onMessage(ByteBuffer message) {
                // onMessage(decodeString(message.array()));
            }

            @Override
            public void onMessage(String message) {
                // appendlnToLog("onTextReceived: " + message);

                ArrayMap<String, Object> map = gson.fromJson(
                    message, new TypeToken<ArrayMap<String, Object>>() {}.getType()
                );

                // obtain sequence number
                Object o = map.get("s");
                if (o != null) {
                    seq = ((Double)o).intValue();
                }

                int opcode = ((Double)map.get("op")).intValue();
                switch (opcode) {
                    case 0: // Dispatch event
                        if (((String)map.get("t")).equals("READY")) {
                            appendlnToLog("Received READY event");
                            Map data = (Map) ((Map)map.get("d")).get("user");
                            appendlnToLog("Connected to " + data.get("username") + "#" + data.get("discriminator"));
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    buttonSetActivity.setEnabled(true);
                                }
                            });
                            return;
                        }
                        break;
                    case 10: // Hello
                        Map data = (Map) map.get("d");
                        heartbeat_interval = ((Double)data.get("heartbeat_interval")).intValue();
                        heartbeatThr = new Thread(heartbeatRunnable);
                        heartbeatThr.start();
                        sendIdentify();
                        break;
                    case 1: // Heartbeat request
                        if (!heartbeatThr.interrupted()) {
                            heartbeatThr.interrupt();
                        }
                        webSocketClient.send(/*encodeString*/("{\"op\":1, \"d\":" + (seq==0?"null":Integer.toString(seq)) + "}"));

                        break;
                    case 11: // Heartbeat ACK
                        if (!heartbeatThr.interrupted()) {
                            heartbeatThr.interrupt();
                        }
                        heartbeatThr = new Thread(heartbeatRunnable);
                        heartbeatThr.start();
                        break;
                }
                //appendlnToLog("Received op " + opcode + ": " + message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                appendlnToLog("Connection closed with exit code " + code + " additional info: " + reason + "\n");
                if (!heartbeatThr.interrupted()) {
                    heartbeatThr.interrupt();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        buttonConnect.setText("Connect");
                        buttonSetActivity.setEnabled(false);
                    }
                });
                throw new RuntimeException("Interrupt");
            }

            @Override
            public void onError(Exception e) {
                if (!e.getMessage().equals("Interrupt")) {
                    appendlnToLog(Log.getStackTraceString(e));
                }
            }

            @Override
            protected void onSetSSLParameters(SSLParameters p) {
                try {
                    super.onSetSSLParameters(p);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        };
        webSocketClient.connect();
    }

    public void appendlnToLog(final String msg) {
        runOnUiThread(new Runnable(){
            @Override
            public void run() {
                textviewLog.append(msg + "\n");
            }
        });
    }

    public void login(View v) {
        if (webView.getVisibility() == View.VISIBLE) {
            webView.stopLoading();
            webView.setVisibility(View.GONE);
            return;
        }
        if (authToken != null) {
            appendlnToLog("Logged in");
            if (v == null) {
                actualConnect();
            }
            return;
        }
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl("https://discord.com/login");
    }

    public void connect(View v) {
        if (!extractToken()) {
            Toast.makeText(this, "Token is not found, opening login page", Toast.LENGTH_SHORT).show();
            login(null);
        } else {
            actualConnect();
        }
    }

    public void actualConnect() {
        if (buttonConnect.getText().equals("Connect")) {
            wsThr = new Thread(new Runnable(){
                @Override
                public void run() {
                    createWebSocketClient();
                }
            });
            buttonConnect.setText("Disconnect");
            wsThr.start();
        } else {
            heartbeatThr.interrupt();
            webSocketClient.close(1000);
            wsThr.interrupt();
            buttonConnect.setText("Connect");
            buttonSetActivity.setEnabled(false);
        }
    }

    public boolean extractToken() {
        // ~~extract token in an ugly way :troll:~~
        try {
            File f = new File(getFilesDir().getParentFile(), "app_webview/Default/Local Storage/leveldb");
            File[] fArr = f.listFiles(new FilenameFilter(){
                @Override
                public boolean accept(File file, String name) {
                    return name.endsWith(".log");
                }
            });
            if (fArr.length == 0) {
                return false;
            }
            f = fArr[0];
            BufferedReader br = new BufferedReader(new FileReader(f));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("token")) {
                    break;
                }
            }
            line = line.substring(line.indexOf("token") + 5);
            line = line.substring(line.indexOf("\"") + 1);
            authToken = line.substring(0, line.indexOf("\""));
            return true;
        } catch (Throwable e) {
            appendlnToLog("Failed extracting token: " + Log.getStackTraceString(e));
            return false;
        }
    }

    /*
     public void code2AccessToken(String code) {
     try {
     HttpURLConnection conn = (HttpURLConnection) new URL("https://discord.com/api/oauth2/token").openConnection();
     conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
     conn.setDoInput(true);

     ArrayMap<String, Object> prop = new ArrayMap<>();
     prop.put("client_id", "591317049637339146");
     prop.put("client_secret", "Discord Android");
     prop.put("grant_type", "authorization_code");
     prop.put("code", code);
     prop.put("redirect_url", "https://account.samsung.com/accounts/oauth/callback");

     byte[] arr = gson.toJson(prop).getBytes();

     conn.getOutputStream().write(arr, 0, arr.length);
     } catch (Throwable e) {
     throw new RuntimeException(e);
     }
     }

    public String decodeString(byte[] arr) {
        Inflater i = new Inflater();
        i.setInput(arr, 0, arr.length);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(arr.length); 
        byte[] buf = new byte[1024];
        try {
            int count = 1;
            while (!i.finished() && count > 0)  { 
                count = i.inflate(buf);
                appendlnToLog("decode " + count);
                bos.write(buf, 0, count); 
            }
        } catch (DataFormatException e) {
            throw new RuntimeException(e);
        }
        i.end();
        try {
            bos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new String(bos.toByteArray());
    }

    public byte[] encodeString(String str) {
        byte[] arr = str.getBytes();
        Deflater compresser = new Deflater();
        compresser.setInput(arr);
        compresser.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(arr.length); 
        // Compress the data 
        byte[] buf = new byte[1024]; 
        while (!compresser.finished())  { 
            int count = compresser.deflate(buf); 
            bos.write(buf, 0, count);
        } 
        compresser.end();
        try {
            bos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        return bos.toByteArray();
    }
 */
}
