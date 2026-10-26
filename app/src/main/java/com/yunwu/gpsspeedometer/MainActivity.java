package com.yunwu.gpsspeedometer;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private LocationManager locationManager;
    private TextView speedText, statusText, statsText, unitText;
    private Button pauseButton;
    private SpeedometerView speedometer;
    private SpeedChartView chart;
    private final ArrayList<Sample> samples = new ArrayList<>();
    private boolean recording = true;
    private boolean paused = false;
    private boolean hasSmoothedSpeed = false;
    private double smoothedKmh = 0;
    private double currentKmh = 0, maxKmh = 0, distanceM = 0;
    private long startMs = 0;
    private long pauseStartedMs = 0, totalPausedMs = 0;
    private Location lastLocation;
    private String unit = "km/h";
    private static final int REQ_LOCATION = 31;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.rgb(16,24,32));
        getWindow().setNavigationBarColor(Color.rgb(16,24,32));
        buildUi();
        startMs = System.currentTimeMillis();
        locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
        } else startGps();
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView label(String s, int size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.CENTER); return t;
    }
    private Button button(String s) {
        Button b = new Button(this); b.setText(s); b.setTextSize(12); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(39,54,66))); return b;
    }
    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(12),dp(8),dp(12),dp(8)); root.setBackgroundColor(Color.rgb(16,24,32));
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = label("GPS SPEED", 20, Color.WHITE); title.setTypeface(null,1); title.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        top.addView(title, new LinearLayout.LayoutParams(0,dp(42),1));
        statusText = label("等待 GPS 定位…", 13, Color.rgb(250,190,80)); top.addView(statusText);
        root.addView(top);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.HORIZONTAL); body.setGravity(Gravity.CENTER); 
        LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL); left.setGravity(Gravity.CENTER); left.setPadding(dp(4),0,dp(8),0);
        LinearLayout speedRow = new LinearLayout(this); speedRow.setGravity(Gravity.CENTER); speedText = label("0", 52, Color.WHITE); speedText.setTypeface(null,1); unitText = label(" km/h",16,Color.LTGRAY); speedRow.addView(speedText); speedRow.addView(unitText); left.addView(speedRow);
        speedometer = new SpeedometerView(this); left.addView(speedometer, new LinearLayout.LayoutParams(-1,0,1));
        statsText = label("最高 0.0   ·   平均 0.0 km/h\n距离 0 m   ·   时长 00:00", 13, Color.rgb(194,205,215)); left.addView(statsText, new LinearLayout.LayoutParams(-1,dp(48)));
        body.addView(left,new LinearLayout.LayoutParams(0,-1,0.95f));
        LinearLayout right = new LinearLayout(this); right.setOrientation(LinearLayout.VERTICAL); right.setPadding(dp(8),dp(4),0,0);
        TextView chartTitle = label("速度曲线 · km/h",15,Color.WHITE); chartTitle.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL); right.addView(chartTitle,new LinearLayout.LayoutParams(-1,dp(30)));
        chart = new SpeedChartView(this); right.addView(chart,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER_VERTICAL); controls.setOrientation(LinearLayout.VERTICAL);
        LinearLayout controlsTop = new LinearLayout(this); controlsTop.setGravity(Gravity.CENTER_VERTICAL);
        pauseButton = button("暂停测速"); pauseButton.setOnClickListener(v -> togglePause()); controlsTop.addView(pauseButton,new LinearLayout.LayoutParams(0,dp(42),1));
        Button reset = button("重新开始"); reset.setOnClickListener(v -> resetSession()); controlsTop.addView(reset,new LinearLayout.LayoutParams(0,dp(42),1));
        Button unitBtn = button("km/h ⇄ mph"); unitBtn.setOnClickListener(v -> { unit = unit.equals("km/h") ? "mph" : "km/h"; unitText.setText(" "+unit); speedText.setText(formatSpeed(currentKmh)); speedometer.invalidate(); }); controlsTop.addView(unitBtn,new LinearLayout.LayoutParams(0,dp(42),1));
        controls.addView(controlsTop,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout controlsBottom = new LinearLayout(this); controlsBottom.setGravity(Gravity.CENTER_VERTICAL);
        Button csv = button("导出 CSV"); csv.setOnClickListener(v -> exportCsv()); controlsBottom.addView(csv,new LinearLayout.LayoutParams(0,dp(42),1));
        Button png = button("导出曲线图"); png.setOnClickListener(v -> exportPng()); controlsBottom.addView(png,new LinearLayout.LayoutParams(0,dp(42),1));
        controls.addView(controlsBottom,new LinearLayout.LayoutParams(-1,dp(44)));
        right.addView(controls); body.addView(right,new LinearLayout.LayoutParams(0,-1,1.45f)); root.addView(body,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
    }
    private String formatSpeed(double kmh) { return String.format(Locale.US, "%.0f", unit.equals("mph") ? kmh*0.621371 : kmh); }
    private void startGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) { statusText.setText("需要定位权限"); return; }
        try {
            if (paused) { statusText.setText("测速已暂停"); return; }
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) statusText.setText("请开启手机定位/GPS");
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 250, 0, listener);
            statusText.setText("正在获取卫星定位…");
        } catch (SecurityException e) { statusText.setText("定位权限不足"); }
        catch (Exception e) { statusText.setText("GPS 暂不可用"); }
    }
    private final LocationListener listener = new LocationListener() {
        @Override public void onLocationChanged(Location loc) {
            if (paused) return;
            // Reject weak fixes before they can create speed spikes or distort the distance.
            if (loc.hasAccuracy() && loc.getAccuracy() > 35f) {
                statusText.setText(String.format(Locale.US,"GPS 信号较弱 · 精度 ±%.0fm（等待更好信号）",loc.getAccuracy()));
                return;
            }
            statusText.setText(String.format(Locale.US,"GPS 已定位 · 精度 ±%.0fm",loc.hasAccuracy()?loc.getAccuracy():0));
            double rawKmh = loc.hasSpeed() ? Math.max(0, loc.getSpeed()*3.6) : 0;
            if (lastLocation != null) {
                long dt = loc.getTime()-lastLocation.getTime();
                float d = loc.distanceTo(lastLocation);
                if (dt > 0 && dt <= 5000 && d < 150) {
                    distanceM += d;
                    if (!loc.hasSpeed()) rawKmh = d * 3.6 / (dt / 1000.0);
                }
            }
            // Light exponential smoothing reduces GPS jitter without making the display too sluggish.
            if (!hasSmoothedSpeed) { smoothedKmh = rawKmh; hasSmoothedSpeed = true; }
            else smoothedKmh = smoothedKmh * 0.65 + rawKmh * 0.35;
            lastLocation = new Location(loc);
            currentKmh = Math.max(0, smoothedKmh);
            maxKmh = Math.max(maxKmh,currentKmh);
            if (recording) {
                samples.add(new Sample(System.currentTimeMillis(),currentKmh,loc.hasAccuracy()?loc.getAccuracy():-1));
                if (samples.size()>3600) samples.remove(0);
            }
            speedText.setText(formatSpeed(currentKmh)); speedometer.setSpeed(currentKmh); chart.invalidate(); updateStats();
        }
        @Override public void onProviderEnabled(String p) { statusText.setText("GPS 已开启，正在定位…"); }
        @Override public void onProviderDisabled(String p) { statusText.setText("GPS 已关闭"); }
    };
    private void updateStats() {
        double sum=0; for(Sample s:samples) sum+=s.speed; double avg=samples.isEmpty()?0:sum/samples.size(); long now=System.currentTimeMillis(); long elapsed=now-startMs-totalPausedMs-(paused && pauseStartedMs>0 ? now-pauseStartedMs : 0); long secs=Math.max(0,elapsed/1000);
        statsText.setText(String.format(Locale.US,"最高 %.1f   ·   平均 %.1f km/h\n距离 %s   ·   时长 %02d:%02d",maxKmh,avg,distanceM>=1000?String.format(Locale.US,"%.2f km",distanceM/1000):String.format(Locale.US,"%.0f m",distanceM),secs/60,secs%60));
    }
    private void togglePause() {
        paused = !paused;
        lastLocation = null;
        hasSmoothedSpeed = false;
        if (paused) {
            pauseStartedMs = System.currentTimeMillis();
            if (locationManager != null) locationManager.removeUpdates(listener);
            currentKmh = 0;
            speedText.setText(formatSpeed(0)); speedometer.setSpeed(0);
            pauseButton.setText("继续测速"); statusText.setText("测速已暂停 · 记录已保留");
            updateStats();
        } else {
            if (pauseStartedMs > 0) totalPausedMs += System.currentTimeMillis() - pauseStartedMs;
            pauseStartedMs = 0;
            pauseButton.setText("暂停测速");
            startGps();
        }
    }
    private void resetSession() {
        samples.clear(); maxKmh=0; distanceM=0; startMs=System.currentTimeMillis(); totalPausedMs=0; pauseStartedMs=paused?startMs:0; lastLocation=null; hasSmoothedSpeed=false; smoothedKmh=0; currentKmh=0;
        speedText.setText(formatSpeed(0)); speedometer.setSpeed(0); chart.invalidate(); updateStats();
        Toast.makeText(this,"已开始新的测速记录",Toast.LENGTH_SHORT).show();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) { super.onRequestPermissionsResult(requestCode,permissions,results); if(requestCode==REQ_LOCATION) { boolean ok=false; for(int r:results) if(r==PackageManager.PERMISSION_GRANTED) ok=true; if(ok) startGps(); else statusText.setText("未授权定位，无法测速"); } }
    @Override protected void onResume() { super.onResume(); if(!paused && locationManager!=null && (Build.VERSION.SDK_INT<23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)) startGps(); }
    @Override protected void onPause() { super.onPause(); if(locationManager!=null) locationManager.removeUpdates(listener); }
    private Uri createExport(String name,String mime) throws Exception {
        ContentValues values=new ContentValues(); values.put(MediaStore.MediaColumns.DISPLAY_NAME,name); values.put(MediaStore.MediaColumns.MIME_TYPE,mime);
        if(Build.VERSION.SDK_INT>=29) values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/GPSSpeedometer");
        return getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
    }
    private String stamp() { return new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date()); }
    private void exportCsv() {
        if(samples.isEmpty()){Toast.makeText(this,"暂无测速数据，请先获得 GPS 定位",Toast.LENGTH_SHORT).show();return;}
        try { Uri uri=createExport("GPS_Speed_"+stamp()+".csv","text/csv"); if(uri==null) throw new Exception("无法创建文件");
            try(OutputStream out=getContentResolver().openOutputStream(uri); OutputStreamWriter w=new OutputStreamWriter(out,StandardCharsets.UTF_8)) {
                w.write("elapsed_seconds,speed_kmh,speed_mph,gps_accuracy_m\n"); long base=samples.get(0).time;
                for(Sample s:samples) w.write(String.format(Locale.US,"%.2f,%.3f,%.3f,%.1f\n",(s.time-base)/1000.0,s.speed,s.speed*0.621371,s.accuracy));
            } Toast.makeText(this,"CSV 已导出到 下载/GPSSpeedometer",Toast.LENGTH_LONG).show();
        } catch(Exception e){Toast.makeText(this,"导出失败："+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void exportPng() {
        if(samples.size()<2){Toast.makeText(this,"至少需要两条 GPS 数据才能导出曲线",Toast.LENGTH_SHORT).show();return;}
        try { int w=1400,h=650; Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(b); c.drawColor(Color.rgb(16,24,32)); Paint p=new Paint(3);
            p.setColor(Color.WHITE);p.setTextSize(38);c.drawText("GPS SPEED HISTORY",55,65,p); p.setTextSize(22);p.setColor(Color.LTGRAY);c.drawText("Speed (km/h)  ·  "+new Date(),55,105,p);
            float l=90,t=145,r=w-45,bot=h-70; p.setStrokeWidth(1);p.setColor(Color.rgb(55,65,81));
            for(int i=0;i<=5;i++){float y=bot-(bot-t)*i/5f;c.drawLine(l,y,r,y,p);p.setTextSize(18);p.setColor(Color.LTGRAY);c.drawText(String.format(Locale.US,"%.0f",maxKmh*i/5),25,y+6,p);p.setColor(Color.rgb(55,65,81));}
            p.setColor(Color.rgb(34,197,94));p.setStrokeWidth(4);p.setStyle(Paint.Style.STROKE);Path path=new Path();
            for(int i=0;i<samples.size();i++){float x=l+(r-l)*i/(samples.size()-1f);float y=bot-(bot-t)*(float)(samples.get(i).speed/Math.max(10,maxKmh*1.1));if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,p);
            Uri uri=createExport("GPS_Speed_Curve_"+stamp()+".png","image/png");if(uri==null)throw new Exception("无法创建文件");try(OutputStream out=getContentResolver().openOutputStream(uri)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();Toast.makeText(this,"曲线图已导出到 下载/GPSSpeedometer",Toast.LENGTH_LONG).show();
        }catch(Exception e){Toast.makeText(this,"导出失败："+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private static class Sample { long time; double speed; float accuracy; Sample(long t,double s,float a){time=t;speed=s;accuracy=a;} }
    private class SpeedometerView extends View {
        Paint p=new Paint(3); double speed=0; SpeedometerView(Activity a){super(a);setLayerType(View.LAYER_TYPE_HARDWARE,null);} void setSpeed(double s){speed=s;invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2,cy=h*.83f,rad=Math.min(w*.43f,h*.9f);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(10));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(Color.rgb(53,65,77));c.drawArc(cx-rad,cy-rad,cx+rad,cy+rad,180,180,false,p);p.setColor(Color.rgb(34,197,94));c.drawArc(cx-rad,cy-rad,cx+rad,cy+rad,180,(float)(180*Math.min(1,speed/240)),false,p);
            p.setStrokeWidth(dp(2));p.setColor(Color.LTGRAY);p.setTextSize(dp(10));p.setStyle(Paint.Style.FILL);for(int i=0;i<=12;i++){double a=Math.PI+(Math.PI*i/12);float x1=(float)(cx+(rad-dp(4))*Math.cos(a)),y1=(float)(cy+(rad-dp(4))*Math.sin(a));float x2=(float)(cx+(rad-dp(15))*Math.cos(a)),y2=(float)(cy+(rad-dp(15))*Math.sin(a));c.drawLine(x1,y1,x2,y2,p);String s=""+(i*20);c.drawText(s,(float)(cx+(rad-dp(28))*Math.cos(a)-dp(7)),(float)(cy+(rad-dp(28))*Math.sin(a)+dp(4)),p);}
            double a=Math.PI+Math.PI*Math.min(1,speed/240);p.setColor(Color.rgb(248,113,113));p.setStrokeWidth(dp(3));c.drawLine(cx,cy,(float)(cx+(rad-dp(28))*Math.cos(a)),(float)(cy+(rad-dp(28))*Math.sin(a)),p);p.setColor(Color.WHITE);c.drawCircle(cx,cy,dp(6),p); }
    }
    private class SpeedChartView extends View {
        Paint p=new Paint(3); SpeedChartView(Activity a){super(a);setBackgroundColor(Color.rgb(16,24,32));}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),l=dp(32),r=w-dp(8),t=dp(12),b=h-dp(25);p.setStrokeWidth(1);p.setColor(Color.rgb(48,61,72));p.setTextSize(dp(10));for(int i=0;i<=4;i++){float y=b-(b-t)*i/4f;c.drawLine(l,y,r,y,p);p.setColor(Color.rgb(160,173,185));c.drawText(String.format(Locale.US,"%.0f",maxKmh*i/4),0,y+dp(4),p);p.setColor(Color.rgb(48,61,72));}if(samples.size()<2)return;double peak=Math.max(20,maxKmh*1.1);p.setColor(Color.rgb(34,197,94));p.setStrokeWidth(dp(2));p.setStyle(Paint.Style.STROKE);Path path=new Path();int start=Math.max(0,samples.size()-240);for(int i=start;i<samples.size();i++){float x=l+(r-l)*(i-start)/(float)Math.max(1,samples.size()-1-start);float y=b-(b-t)*(float)(samples.get(i).speed/peak);if(i==start)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
    }
}
