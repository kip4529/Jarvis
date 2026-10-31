package com.jarvis.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    static final String SYS = "You are Jarvis, a witty, concise phone assistant. Keep answers under 3 sentences.";
    TextToSpeech tts;
    boolean ttsReady = false;
    TextView log;
    ScrollView scroll;
    EditText input;
    List<JSONObject> hist = new ArrayList<>();
    Handler ui = new Handler(Looper.getMainLooper());

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tts = new TextToSpeech(this, this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B0F14"));
        root.setFitsSystemWindows(true);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));
        scroll = new ScrollView(this);
        log = new TextView(this);
        log.setTextColor(Color.WHITE);
        log.setTextSize(16);
        log.setText("Jarvis ready. Tap the mic or type.\nTry: alarm 7:30 am, timer 5 minutes, play lofi, search weather, open youtube.\n\n");
        scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        input = new EditText(this);
        input.setHint("Type here...");
        input.setHintTextColor(Color.GRAY);
        input.setTextColor(Color.WHITE);
        input.setSingleLine(true);
        row.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        Button send = new Button(this);
        send.setText("Send");
        send.setOnClickListener(v -> {
            String t = input.getText().toString().trim();
            input.setText("");
            if (!t.isEmpty()) submit(t);
        });
        row.addView(send);
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        Button mic = new Button(this);
        mic.setText("TAP TO TALK");
        mic.setTextSize(20);
        mic.setGravity(Gravity.CENTER);
        mic.setOnClickListener(v -> listen());
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, dp(110));
        mp.topMargin = dp(10);
        root.addView(mic, mp);
        setContentView(root);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.getDefault());
            ttsReady = true;
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    void listen() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Jarvis");
        try {
            startActivityForResult(i, 1);
        } catch (Exception e) {
            reply("Speech recognition is not available. Type instead.");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 1 && res == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) submit(r.get(0));
        }
    }

    void append(String s) {
        log.append(s + "\n\n");
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    void reply(String s) {
        append("Jarvis: " + s);
        if (ttsReady) tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "j");
    }

    void submit(String t) {
        append("You: " + t);
        if (!command(t)) chat(t);
    }

    void go(Intent i, String ok) {
        try {
            startActivity(i);
            reply(ok);
        } catch (Exception e) {
            reply("I could not do that on this phone.");
        }
    }

    String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    boolean command(String t) {
        String s = t.toLowerCase().trim();
        Matcher m = Pattern.compile("(?:call|dial)\\s+(\\+?[\\d ]{3,})").matcher(s);
        if (m.find()) {
            go(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + m.group(1).replace(" ", ""))), "Opening dialer.");
            return true;
        }
        m = Pattern.compile("(?:text|sms|message)\\s+(\\+?[\\d ]{3,}?)\\s+(?:saying|that|say)?\\s*(.+)", Pattern.CASE_INSENSITIVE).matcher(t);
        if (m.find()) {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("sms:" + m.group(1).replace(" ", "")));
            i.putExtra("sms_body", m.group(2));
            go(i, "Message ready. Tap send.");
            return true;
        }
        m = Pattern.compile("timer\\D*(\\d+)\\s*(second|sec|minute|min|hour|hr)").matcher(s);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            String u = m.group(2);
            int secs = u.startsWith("sec") ? n : u.startsWith("h") ? n * 3600 : n * 60;
            Intent i = new Intent(AlarmClock.ACTION_SET_TIMER);
            i.putExtra(AlarmClock.EXTRA_LENGTH, secs);
            i.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            go(i, "Timer set for " + n + " " + u + ".");
            return true;
        }
        m = Pattern.compile("(?:alarm|wake me)\\D*?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").matcher(s.replace(".", ""));
        if (m.find()) {
            int h = Integer.parseInt(m.group(1));
            int mi = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            String ap = m.group(3) == null ? "" : m.group(3);
            if (ap.equals("pm") && h < 12) h += 12;
            if (ap.equals("am") && h == 12) h = 0;
            Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
            i.putExtra(AlarmClock.EXTRA_HOUR, h);
            i.putExtra(AlarmClock.EXTRA_MINUTES, mi);
            go(i, String.format("Alarm for %02d:%02d.", h, mi));
            return true;
        }
        m = Pattern.compile("(?:play|youtube)\\s+(.+)").matcher(s);
        if (m.find()) {
            go(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + enc(m.group(1)))), "Opening YouTube.");
            return true;
        }
        m = Pattern.compile("(?:search|google)\\s+(?:for\\s+)?(.+)").matcher(s);
        if (m.find()) {
            go(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + enc(m.group(1)))), "Searching.");
            return true;
        }
        m = Pattern.compile("open\\s+(\\S+)").matcher(s);
        if (m.find()) {
            String x = m.group(1);
            String url = x.startsWith("http") ? x : "https://" + (x.contains(".") ? x : "www." + x + ".com");
            go(new Intent(Intent.ACTION_VIEW, Uri.parse(url)), "Opening " + x + ".");
            return true;
        }
        return false;
    }

    JSONObject obj(String role, String content) {
        JSONObject o = new JSONObject();
        try { o.put("role", role); o.put("content", content); } catch (Exception e) { }
        return o;
    }

    void chat(String t) {
        hist.add(obj("user", t));
        while (hist.size() > 10) hist.remove(0);
        final List<JSONObject> snap = new ArrayList<>(hist);
        new Thread(() -> {
            try {
                JSONArray a = new JSONArray();
                a.put(obj("system", SYS));
                for (JSONObject o : snap) a.put(o);
                JSONObject b = new JSONObject();
                b.put("messages", a);
                b.put("max_tokens", 200);
                b.put("temperature", 0.7);
                HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/v1/chat/completions").openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                c.setConnectTimeout(3000);
                c.setReadTimeout(300000);
                c.getOutputStream().write(b.toString().getBytes("UTF-8"));
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = r.readLine()) != null) sb.append(l);
                final String rep = new JSONObject(sb.toString()).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").getString("content").trim();
                hist.add(obj("assistant", rep));
                ui.post(() -> reply(rep));
            } catch (Exception e) {
                ui.post(() -> reply("My brain is offline. Open Termux and type: jarvis-brain"));
            }
        }).start();
    }
}
