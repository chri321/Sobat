package com.example.map;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;
import com.example.map.sensor.CollisionDetectionService;
import com.google.android.material.navigation.NavigationView;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // 页面容器
    private LinearLayout pageHome;
    private LinearLayout pageMap;

    // 导航项
    private LinearLayout navHome, navMap, navAi, navCommunity, navProfile;
    private TextView navHomeText, navMapText, navAiText, navCommunityText, navProfileText;
    private ImageView navHomeIcon, navMapIcon, navAiIcon, navCommunityIcon, navProfileIcon;
    private TextView[] navTexts;
    private ImageView[] navIcons;

    // 侧滑抽屉
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle drawerToggle;

    // 地图
    private MapView mapView;
    private AMap aMap;
    private boolean firstLocate = true;

    private static final int REQ_LOCATION = 100;
    private static final int REQ_NOTIFICATION = 101;

    // 分类常量
    private static final int TYPE_ALL = 0;
    private static final int TYPE_REPAIR = 1;
    private static final int TYPE_HOSPITAL = 2;
    private static final int TYPE_CHARGE = 3;

    // 模拟 POI 数据: {名称, 描述, 分类}
    private static final String[][] MARKER_DATA = {
            {"途虎养车工场店", "⭐4.7 连锁",        "repair"},
            {"天猫养车旗舰店",  "⭐4.9 旗舰",        "repair"},
            {"华西医院",        "三甲·排队≈32min",   "hospital"},
            {"成都市一医院",    "三甲·急诊24h",      "hospital"},
            {"特来电充电站",    "快充120kW·24h",     "charge"},
            {"星星充电站",      "快充60kW·空闲16枪",  "charge"},
            {"小桔充电",        "快充90kW·¥1.5/度",  "charge"},
            {"精典汽车维修",    "⭐4.3 综合维修",     "repair"},
            {"省人民医院",      "三甲·排队≈20min",   "hospital"},
            {"国家电网充电站",  "快充60kW·¥1.0/度",  "charge"},
    };

    // 当前分类和 marker 列表
    private int currentType = TYPE_ALL;
    private final java.util.List<Marker> markerList = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MapsInitializer.updatePrivacyShow(this, true,true);
        MapsInitializer.updatePrivacyAgree(this, true);

        initViews();
        initNavBar();
        initDrawer();
        selectTab(navMap, navMapText);

        requestLocationPermission();
        requestNotificationPermission();

        // 处理碰撞检测触发的 Intent
        if (getIntent().getBooleanExtra(CollisionDetectionService.EXTRA_COLLISION_ALERT, false)) {
            showCollisionDialog();
        }

        // 如果碰撞检测之前是开启的，自动启动服务
        SharedPreferences prefs = getSharedPreferences(CollisionDetectionService.PREF_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(CollisionDetectionService.PREF_COLLISION_ENABLED, true)) {
            startCollisionServiceIfNeeded();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(CollisionDetectionService.EXTRA_COLLISION_ALERT, false)) {
            showCollisionDialog();
        }
    }

    // ==================== 定位权限 ====================

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            initMap(null);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, REQ_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initMap(null);
            } else {
                Toast.makeText(this, "需要定位权限才能显示当前位置", Toast.LENGTH_LONG).show();
                initMap(null);
            }
        } else if (requestCode == REQ_NOTIFICATION) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "通知权限被拒绝，碰撞预警可能无法正常弹窗", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==================== 视图初始化 ====================

    private void initViews() {
        pageHome = findViewById(R.id.page_home);
        pageMap = findViewById(R.id.page_map);

        navHome = findViewById(R.id.nav_home);
        navMap = findViewById(R.id.nav_map);
        navAi = findViewById(R.id.nav_ai);
        navCommunity = findViewById(R.id.nav_community);
        navProfile = findViewById(R.id.nav_profile);

        navHomeText = findViewById(R.id.nav_home_text);
        navMapText = findViewById(R.id.nav_map_text);
        navAiText = findViewById(R.id.nav_ai_text);
        navCommunityText = findViewById(R.id.nav_community_text);
        navProfileText = findViewById(R.id.nav_profile_text);

        navHomeIcon = findViewById(R.id.nav_home_icon);
        navMapIcon = findViewById(R.id.nav_map_icon);
        navAiIcon = findViewById(R.id.nav_ai_icon);
        navCommunityIcon = findViewById(R.id.nav_community_icon);
        navProfileIcon = findViewById(R.id.nav_profile_icon);

        navTexts = new TextView[]{navHomeText, navMapText, navAiText, navCommunityText, navProfileText};
        navIcons = new ImageView[]{navHomeIcon, navMapIcon, navAiIcon, navCommunityIcon, navProfileIcon};

        // 分类按钮
        initCategories();

        // 卡片列表
        cardRepair1 = findViewById(R.id.card_repair_1);
        cardRepair2 = findViewById(R.id.card_repair_2);
        cardHospital1 = findViewById(R.id.card_hospital_1);
        cardHospital2 = findViewById(R.id.card_hospital_2);
        cardCharge1 = findViewById(R.id.card_charge_1);
        cardCharge2 = findViewById(R.id.card_charge_2);
        cardCharge3 = findViewById(R.id.card_charge_3);
        allCards = new View[]{cardRepair1, cardRepair2, cardHospital1, cardHospital2,
                cardCharge1, cardCharge2, cardCharge3};
    }

    private TextView catAllIcon, catRepairIcon, catHospitalIcon, catChargeIcon;
    private TextView catAllText, catRepairText, catHospitalText, catChargeText;
    private TextView[] catIcons, catTexts;
    private int currentCat = TYPE_ALL;

    private void initCategories() {
        catAllIcon = findViewById(R.id.cat_all_icon);
        catRepairIcon = findViewById(R.id.cat_repair_icon);
        catHospitalIcon = findViewById(R.id.cat_hospital_icon);
        catChargeIcon = findViewById(R.id.cat_charge_icon);

        catAllText = findViewById(R.id.cat_all_text);
        catRepairText = findViewById(R.id.cat_repair_text);
        catHospitalText = findViewById(R.id.cat_hospital_text);
        catChargeText = findViewById(R.id.cat_charge_text);

        catIcons = new TextView[]{catAllIcon, catRepairIcon, catHospitalIcon, catChargeIcon};
        catTexts = new TextView[]{catAllText, catRepairText, catHospitalText, catChargeText};

        findViewById(R.id.cat_all).setOnClickListener(v -> selectCategory(TYPE_ALL));
        findViewById(R.id.cat_repair).setOnClickListener(v -> selectCategory(TYPE_REPAIR));
        findViewById(R.id.cat_hospital).setOnClickListener(v -> selectCategory(TYPE_HOSPITAL));
        findViewById(R.id.cat_charge).setOnClickListener(v -> selectCategory(TYPE_CHARGE));
    }

    // 卡片列表
    private View cardRepair1, cardRepair2, cardHospital1, cardHospital2, cardCharge1, cardCharge2, cardCharge3;
    private View[] allCards;

    private void selectCategory(int type) {
        currentCat = type;
        filterMarkers(type);
        filterCards(type);

        for (int i = 0; i < catTexts.length; i++) {
            if (i == type) {
                catTexts[i].setTextColor(Color.parseColor("#FF6A00"));
                catTexts[i].setTypeface(Typeface.DEFAULT_BOLD);
                catIcons[i].setBackgroundResource(
                        type == TYPE_HOSPITAL ? R.drawable.bg_icon_red : R.drawable.bg_icon_orange);
            } else {
                catTexts[i].setTextColor(Color.parseColor("#333333"));
                catTexts[i].setTypeface(Typeface.DEFAULT);
                catIcons[i].setBackgroundResource(R.drawable.bg_icon_orange);
            }
        }
    }

    private void filterCards(int type) {
        switch (type) {
            case TYPE_ALL:
                for (View v : allCards) v.setVisibility(View.VISIBLE);
                break;
            case TYPE_REPAIR:
                for (View v : allCards) v.setVisibility(View.GONE);
                cardRepair1.setVisibility(View.VISIBLE);
                cardRepair2.setVisibility(View.VISIBLE);
                break;
            case TYPE_HOSPITAL:
                for (View v : allCards) v.setVisibility(View.GONE);
                cardHospital1.setVisibility(View.VISIBLE);
                cardHospital2.setVisibility(View.VISIBLE);
                break;
            case TYPE_CHARGE:
                for (View v : allCards) v.setVisibility(View.GONE);
                cardCharge1.setVisibility(View.VISIBLE);
                cardCharge2.setVisibility(View.VISIBLE);
                cardCharge3.setVisibility(View.VISIBLE);
                break;
        }
    }

    // ==================== 地图 ====================

    @SuppressWarnings("deprecation")
    private void initMap(Bundle savedInstanceState) {
        mapView = findViewById(R.id.map);
        mapView.onCreate(savedInstanceState);

        aMap = mapView.getMap();

        UiSettings ui = aMap.getUiSettings();
        ui.setZoomControlsEnabled(true);
        ui.setMyLocationButtonEnabled(true);

        MyLocationStyle style = new MyLocationStyle();
        style.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER);
        style.interval(2000);
        style.showMyLocation(true);
        aMap.setMyLocationStyle(style);
        aMap.setMyLocationEnabled(true);

        // 点击 marker 弹 info window
        aMap.setOnMarkerClickListener(marker -> {
            marker.showInfoWindow();
            return true;
        });

        // 首次定位后生成随机 marker
        aMap.setOnMyLocationChangeListener(location -> {
            if (location == null) return;
            double lat = location.getLatitude();
            double lng = location.getLongitude();
            if (lat == 0.0 && lng == 0.0) return;
            if (firstLocate) {
                firstLocate = false;
                LatLng center = new LatLng(lat, lng);
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 16));
                addRandomMarkers(center);
            }
        });
    }

    private void addRandomMarkers(LatLng center) {
        Random r = new Random();

        for (String[] data : MARKER_DATA) {
            double offsetLat = (r.nextDouble() - 0.5) * 0.03;
            double offsetLng = (r.nextDouble() - 0.5) * 0.03;

            LatLng pos = new LatLng(
                    center.latitude + offsetLat,
                    center.longitude + offsetLng);

            // 3 种颜色对应 3 个分类
            float hue;
            switch (data[2]) {
                case "repair":   hue = BitmapDescriptorFactory.HUE_ORANGE; break;
                case "hospital": hue = BitmapDescriptorFactory.HUE_RED;    break;
                case "charge":   hue = BitmapDescriptorFactory.HUE_AZURE;  break;
                default:         hue = BitmapDescriptorFactory.HUE_ORANGE;
            }

            Marker marker = aMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(data[0])
                    .snippet(data[1])
                    .icon(BitmapDescriptorFactory.defaultMarker(hue)));

            // 用 snippet 存储分类，供过滤用
            marker.setObject(data[2]);
            markerList.add(marker);
        }
    }

    private void filterMarkers(int type) {
        currentType = type;
        for (Marker m : markerList) {
            if (type == TYPE_ALL) {
                m.setVisible(true);
            } else {
                String cat = (String) m.getObject();
                switch (type) {
                    case TYPE_REPAIR:   m.setVisible("repair".equals(cat));   break;
                    case TYPE_HOSPITAL: m.setVisible("hospital".equals(cat)); break;
                    case TYPE_CHARGE:   m.setVisible("charge".equals(cat));   break;
                }
            }
        }
    }

    // ==================== 导航栏 ====================

    private void initNavBar() {
        navHome.setOnClickListener(v -> {
            selectTab(navHome, navHomeText);
            pageHome.setVisibility(View.VISIBLE);
            pageMap.setVisibility(View.GONE);
        });

        navMap.setOnClickListener(v -> {
            selectTab(navMap, navMapText);
            pageHome.setVisibility(View.GONE);
            pageMap.setVisibility(View.VISIBLE);
        });

        navAi.setOnClickListener(v -> {
            selectTab(navAi, navAiText);
            Toast.makeText(this, "问AI — 即将上线", Toast.LENGTH_SHORT).show();
        });

        navCommunity.setOnClickListener(v -> {
            selectTab(navCommunity, navCommunityText);
            Toast.makeText(this, "社群 — 即将上线", Toast.LENGTH_SHORT).show();
        });

        navProfile.setOnClickListener(v -> {
            selectTab(navProfile, navProfileText);
            Toast.makeText(this, "个人中心 — 即将上线", Toast.LENGTH_SHORT).show();
        });
    }

    private void selectTab(View tab, TextView tabText) {
        int inactiveColor = Color.parseColor("#999999");
        int activeColor = Color.parseColor("#FF6A00");
        for (TextView tv : navTexts) {
            tv.setTextColor(inactiveColor);
            tv.setTypeface(Typeface.DEFAULT);
        }
        for (ImageView iv : navIcons) {
            iv.setColorFilter(inactiveColor);
        }
        tabText.setTextColor(activeColor);
        tabText.setTypeface(Typeface.DEFAULT_BOLD);
        ImageView activeIcon = (ImageView) ((LinearLayout) tab).getChildAt(0);
        if (activeIcon == navAiIcon) {
            activeIcon.setColorFilter(Color.parseColor("#FFFFFF"));
        } else {
            activeIcon.setColorFilter(activeColor);
        }
    }

    // ========== 侧滑抽屉菜单 & 碰撞检测 ==========

    private void initDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // 汉堡按钮点击打开抽屉
        TextView btnHamburger = findViewById(R.id.btn_hamburger);
        if (btnHamburger != null) {
            btnHamburger.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        // ActionBarDrawerToggle（抽屉开关动画 / 同步状态）
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout,
                R.string.app_name, R.string.app_name);
        drawerLayout.addDrawerListener(drawerToggle);

        // 抽屉菜单项点击处理
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            drawerLayout.closeDrawer(GravityCompat.START);

            if (id == R.id.drawer_settings) {
                // 设置：跳转设置页
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            } else if (id == R.id.drawer_home) {
                selectTab(navHome, navHomeText);
                pageHome.setVisibility(View.VISIBLE);
                pageMap.setVisibility(View.GONE);
            } else if (id == R.id.drawer_map) {
                selectTab(navMap, navMapText);
                pageHome.setVisibility(View.GONE);
                pageMap.setVisibility(View.VISIBLE);
            } else if (id == R.id.drawer_ai) {
                selectTab(navAi, navAiText);
                Toast.makeText(this, "问AI — 即将上线", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.drawer_community) {
                selectTab(navCommunity, navCommunityText);
                Toast.makeText(this, "社群 — 即将上线", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.drawer_profile) {
                selectTab(navProfile, navProfileText);
                Toast.makeText(this, "个人中心 — 即将上线", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_NOTIFICATION);
            }
        }
    }

    private void startCollisionServiceIfNeeded() {
        Intent intent = new Intent(this, CollisionDetectionService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void showCollisionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("检测到碰撞！")
                .setMessage("系统检测到异常加速度变化，你是否遭遇危险？")
                .setCancelable(false)
                .setPositiveButton("拨打 120", (dialog, which) -> {
                    try {
                        Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                        dialIntent.setData(Uri.parse("tel:120"));
                        startActivity(dialIntent);
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("我没事", (dialog, which) -> dialog.dismiss())
                .show();
    }

    // ========== 生命周期 ==========

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (drawerToggle != null) {
            drawerToggle.syncState();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapView != null) mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }
}
