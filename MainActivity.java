package com.osmanli.browser;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.*;
import android.webkit.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private final int navy = Color.rgb(5, 18, 35);
    private final int panel = Color.rgb(9, 31, 55);
    private final int gold = Color.rgb(235, 184, 91);
    private LinearLayout root, top, homePanel, bottom;
    private EditText address;
    private WebView web;
    private TextView status;
    private boolean home = true, privateMode = false;
    private int tabCount = 1;
    private ArrayList<String> history = new ArrayList<>();
    private ArrayList<String> bookmarks = new ArrayList<>();
    private android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(navy);
        getWindow().setNavigationBarColor(navy);
        prefs = getSharedPreferences("osmanli_browser", MODE_PRIVATE);
        loadLists();
        buildUI();
        showHome();
    }

    private GradientDrawable shape(int color, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(18));
        d.setStroke(dp(1), stroke);
        return d;
    }
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String s, int size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size);
        t.setTextColor(color); t.setGravity(Gravity.CENTER); return t;
    }
    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }
    private void buildUI() {
        root = vertical(); root.setBackgroundColor(navy);
        setContentView(root);
        top = vertical(); top.setPadding(dp(12),dp(8),dp(12),dp(8));
        root.addView(top, new LinearLayout.LayoutParams(-1,-2));

        LinearLayout bar = horizontal();
        bar.setBackground(shape(panel,gold)); bar.setPadding(dp(10),dp(3),dp(8),dp(3));
        address = new EditText(this); address.setSingleLine(true);
        address.setTextColor(Color.WHITE); address.setHintTextColor(0xffaebbd0);
        address.setHint("Ara veya URL yaz"); address.setTextSize(16);
        address.setBackgroundColor(Color.TRANSPARENT);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        bar.addView(address,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView go=label("➜",25,gold); bar.addView(go,new LinearLayout.LayoutParams(dp(42),dp(48)));
        go.setOnClickListener(v -> navigateFromAddress());
        address.setOnEditorActionListener((v,action,event)->{ if(action==EditorInfo.IME_ACTION_GO || (event!=null && event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){ navigateFromAddress(); return true;} return false;});
        top.addView(bar);

        FrameLayout content = new FrameLayout(this);
        root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        homePanel = vertical(); homePanel.setGravity(Gravity.CENTER_HORIZONTAL);
        homePanel.setPadding(dp(14),dp(8),dp(14),dp(8));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(homePanel);
        content.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        web = new WebView(this);
        web.setBackgroundColor(navy);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setLoadWithOverviewMode(true);
        web.getSettings().setUseWideViewPort(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String u=req.getUrl().toString();
                if(u.startsWith("http://")||u.startsWith("https://")) return false;
                return true;
            }
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){ home=false; homePanel.setVisibility(View.GONE); web.setVisibility(View.VISIBLE); address.setText(url); }
            @Override public void onPageFinished(WebView v,String url){ address.setText(url); if(!privateMode && url.startsWith("http")) addHistory(url); }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView v,int p){ status.setText(p<100 ? "Sayfa yükleniyor: "+p+"%" : "Güvenli tarama"); }
        });
        web.setDownloadListener((url,ua,cd,mime,len)->{
            try {
                DownloadManager.Request req=new DownloadManager.Request(Uri.parse(url));
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                String name=URLUtil.guessFileName(url,cd,mime);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name);
                ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
                Toast.makeText(this,"İndirme başlatıldı: "+name,Toast.LENGTH_LONG).show();
            } catch(Exception e){ Toast.makeText(this,"İndirme başlatılamadı",Toast.LENGTH_SHORT).show(); }
        });
        content.addView(web,new FrameLayout.LayoutParams(-1,-1)); web.setVisibility(View.GONE);

        bottom=horizontal(); bottom.setGravity(Gravity.CENTER); bottom.setPadding(dp(4),dp(7),dp(4),dp(7));
        bottom.setBackgroundColor(0xff04101f);
        root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(62)));
        addNav("⌂","Ana Sayfa",()->showHome());
        addNav("☆","Favoriler",()->showList("Favoriler",bookmarks));
        addNav("◷","Geçmiş",()->showList("Geçmiş",history));
        addNav("▢","Sekmeler",()->{
            new AlertDialog.Builder(this).setTitle("Sekmeler ("+tabCount+")")
              .setMessage("Basit sürümde tek etkin web sekmesi bulunur.")
              .setPositiveButton("Yeni sekme", (d,w)->{tabCount++; showHome();})
              .setNegativeButton("Kapat",null).show();
        });
        addNav("☰","Menü",()->showMenu());
    }
    private void addNav(String icon,String title,Runnable action) {
        LinearLayout item=vertical(); item.setGravity(Gravity.CENTER);
        item.addView(label(icon,23,gold)); item.addView(label(title,11,Color.WHITE));
        bottom.addView(item,new LinearLayout.LayoutParams(0,-1,1));
        item.setOnClickListener(v->action.run());
    }
    private void showHome() {
        home=true; web.setVisibility(View.GONE); homePanel.setVisibility(View.VISIBLE);
        address.setText("");
        homePanel.removeAllViews();
        ImageView art=new ImageView(this); art.setImageResource(R.drawable.home_art);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(175)); ap.bottomMargin=dp(8);
        homePanel.addView(art,ap);
        ImageView emblem=new ImageView(this); emblem.setImageResource(R.drawable.osmanli_emblem);
        emblem.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(dp(190),dp(190)); ep.gravity=Gravity.CENTER; ep.bottomMargin=dp(2);
        homePanel.addView(emblem,ep);
        TextView title=label("OSMANLI TARAYICI",25,gold); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        homePanel.addView(title);
        TextView sub=label("Geçmişin gücü, geleceğin teknolojisi",13,0xffe4d4b4); homePanel.addView(sub);
        LinearLayout actions=horizontal(); actions.setGravity(Gravity.CENTER); actions.setPadding(0,dp(14),0,dp(12));
        addHomeButton(actions,"★\nFavoriler",()->showList("Favoriler",bookmarks));
        addHomeButton(actions,"◷\nGeçmiş",()->showList("Geçmiş",history));
        addHomeButton(actions,"↓\nİndirilenler",()->openDownloads());
        addHomeButton(actions,"⚙\nAyarlar",()->showMenu());
        homePanel.addView(actions);
        TextView quick=label("HIZLI ERİŞİM",14,gold); quick.setTypeface(null,Typeface.BOLD); homePanel.addView(quick);
        LinearLayout links=horizontal(); links.setGravity(Gravity.CENTER);
        addHomeButton(links,"Google",()->load("https://www.google.com"));
        addHomeButton(links,"YouTube",()->load("https://www.youtube.com"));
        addHomeButton(links,"Instagram",()->load("https://www.instagram.com"));
        homePanel.addView(links);
        status=label("Güvenli tarama • Osmanlı teması",12,0xffb9c8dc);
        status.setPadding(0,dp(15),0,dp(6)); homePanel.addView(status);
    }
    private void addHomeButton(LinearLayout parent,String text,Runnable r) {
        TextView b=label(text,12,Color.WHITE); b.setBackground(shape(panel,gold));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(62),1);
        p.setMargins(dp(3),dp(3),dp(3),dp(3)); parent.addView(b,p); b.setOnClickListener(v->r.run());
    }
    private void navigateFromAddress() {
        String s=address.getText().toString().trim(); if(s.isEmpty()) return;
        if(!s.contains(" ") && s.matches("(?i)^[a-z0-9.-]+\\.[a-z]{2,}.*")) {
            if(!s.startsWith("http://")&&!s.startsWith("https://")) s="https://"+s;
        } else s="https://www.google.com/search?q="+Uri.encode(s);
        load(s);
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);
    }
    private void load(String url) {
        home=false; homePanel.setVisibility(View.GONE); web.setVisibility(View.VISIBLE);
        address.setText(url); web.loadUrl(url);
    }
    private void showList(String title,ArrayList<String> data) {
        if(data.isEmpty()){ new AlertDialog.Builder(this).setTitle(title).setMessage("Henüz kayıt yok.").setPositiveButton("Tamam",null).show(); return; }
        String[] items=data.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle(title).setItems(items,(d,w)->load(items[w]))
          .setNeutralButton("Temizle",(d,w)->{data.clear(); saveLists(); Toast.makeText(this,"Liste temizlendi",Toast.LENGTH_SHORT).show();}).setNegativeButton("Kapat",null).show();
    }
    private void showMenu() {
        String[] items={"Gizli modu aç/kapat","Bu sayfayı favorilere ekle","İndirilenler","Sayfayı yenile","Ana sayfa"};
        new AlertDialog.Builder(this).setTitle("Osmanlı Tarayıcı").setItems(items,(d,w)->{
            if(w==0){privateMode=!privateMode; Toast.makeText(this,privateMode?"Gizli mod açık":"Gizli mod kapalı",Toast.LENGTH_SHORT).show();}
            if(w==1){String u=web.getUrl(); if(u!=null){bookmarks.add(0,u);saveLists();Toast.makeText(this,"Favorilere eklendi",Toast.LENGTH_SHORT).show();}}
            if(w==2)openDownloads();
            if(w==3 && !home)web.reload();
            if(w==4)showHome();
        }).show();
    }
    private void openDownloads() {
        try { Intent i=new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS); startActivity(i); }
        catch(Exception e){ Toast.makeText(this,"İndirme yöneticisi açılamadı",Toast.LENGTH_SHORT).show(); }
    }
    private void addHistory(String u) {
        if(history.isEmpty() || !history.get(0).equals(u)){history.add(0,u); if(history.size()>100)history.remove(history.size()-1); saveLists();}
    }
    private void loadLists() {
        history=new ArrayList<>(prefs.getStringSet("history",new HashSet<>()));
        bookmarks=new ArrayList<>(prefs.getStringSet("bookmarks",new HashSet<>()));
    }
    private void saveLists() {
        prefs.edit().putStringSet("history",new HashSet<>(history)).putStringSet("bookmarks",new HashSet<>(bookmarks)).apply();
    }
    @Override public void onBackPressed() {
        if(web.getVisibility()==View.VISIBLE && web.canGoBack()) web.goBack();
        else showHome();
    }
}
