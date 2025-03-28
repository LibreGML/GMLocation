package tz.gml.gmlocation;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.view.Gravity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Locale;
import java.util.List;
import java.util.Map;

import tz.gml.database.DataBaseHistoryLocation;
import tz.gml.utils.GoUtils;

public class HistoryActivity extends BaseActivity {
    public static final String KEY_ID = "KEY_ID";
    public static final String KEY_LOCATION = "KEY_LOCATION";
    public static final String KEY_TIME = "KEY_TIME";
    public static final String KEY_LNG_LAT_WGS = "KEY_LNG_LAT_WGS";
    public static final String KEY_LNG_LAT_CUSTOM = "KEY_LNG_LAT_CUSTOM";

    private ListView mRecordListView;
    private TextView noRecordText;
    private LinearLayout mSearchLayout;
    private SQLiteDatabase mHistoryLocationDB;
    private List<Map<String, Object>> mAllRecord;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#201D26"));


        setContentView(R.layout.activity_history);

        ActionBar actionBar = getSupportActionBar();

        if(actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        initLocationDataBase();

        initSearchView();

        initRecordListView();
    }

    @Override
    protected void onDestroy() {
        mHistoryLocationDB.close();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this add items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_history, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            this.finish(); // back button
            return true;
        } else if (id ==  R.id.action_delete) {
            new AlertDialog.Builder(HistoryActivity.this)
                    .setTitle("警告")//这里是表头的内容
                    .setMessage("确定要删除全部历史记录吗?")//这里是中间显示的具体信息
                    .setPositiveButton("确定",
                            (dialog, which) -> {
                                if (deleteRecord(-1)) {
                                    GoUtils.DisplayToast(this, getResources().getString(R.string.history_delete_ok));
                                    updateRecordList();
                                }
                            })
                    .setNegativeButton("取消",
                            (dialog, which) -> {
                            })
                    .show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void initLocationDataBase() {
        try {
            try (DataBaseHistoryLocation hisLocDBHelper = new DataBaseHistoryLocation(getApplicationContext())) {
                mHistoryLocationDB = hisLocDBHelper.getWritableDatabase();
            }
        } catch (Exception e) {
            Log.e("HistoryActivity", "ERROR - initLocationDataBase");
        }

        recordArchive();
    }

    //sqlite 操作 查询所有记录
    private List<Map<String, Object>> fetchAllRecord() {
        List<Map<String, Object>> data = new ArrayList<>();

        try {
            Cursor cursor = mHistoryLocationDB.query(DataBaseHistoryLocation.TABLE_NAME, null,
                    DataBaseHistoryLocation.DB_COLUMN_ID + " > ?", new String[] {"0"},
                    null, null, DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP + " DESC", null);

            while (cursor.moveToNext()) {
                Map<String, Object> item = new HashMap<>();
                int ID = cursor.getInt(0);
                String Location = cursor.getString(1);
                String Longitude = cursor.getString(2);
                String Latitude = cursor.getString(3);
                long TimeStamp = cursor.getInt(4);
                String BD09Longitude = cursor.getString(5);
                String BD09Latitude = cursor.getString(6);
                Log.d("TB", ID + "\t" + Location + "\t" + Longitude + "\t" + Latitude + "\t" + TimeStamp + "\t" + BD09Longitude + "\t" + BD09Latitude);
                BigDecimal bigDecimalLongitude = BigDecimal.valueOf(Double.parseDouble(Longitude));
                BigDecimal bigDecimalLatitude = BigDecimal.valueOf(Double.parseDouble(Latitude));
                BigDecimal bigDecimalBDLongitude = BigDecimal.valueOf(Double.parseDouble(BD09Longitude));
                BigDecimal bigDecimalBDLatitude = BigDecimal.valueOf(Double.parseDouble(BD09Latitude));
                double doubleLongitude = bigDecimalLongitude.setScale(11, RoundingMode.HALF_UP).doubleValue();
                double doubleLatitude = bigDecimalLatitude.setScale(11, RoundingMode.HALF_UP).doubleValue();
                double doubleBDLongitude = bigDecimalBDLongitude.setScale(11, RoundingMode.HALF_UP).doubleValue();
                double doubleBDLatitude = bigDecimalBDLatitude.setScale(11, RoundingMode.HALF_UP).doubleValue();
                item.put(KEY_ID, Integer.toString(ID));
                item.put(KEY_LOCATION, Location);
                item.put(KEY_TIME, GoUtils.timeStamp2Date(Long.toString(TimeStamp)));
                item.put(KEY_LNG_LAT_WGS, "[经度:" + doubleLongitude + " 纬度:" + doubleLatitude + "]");
                item.put(KEY_LNG_LAT_CUSTOM, "[经度:" + doubleBDLongitude + " 纬度:" + doubleBDLatitude + "]");
                data.add(item);
            }
            cursor.close();
        } catch (Exception e) {
            data.clear();
            Log.e("HistoryActivity", "ERROR - fetchAllRecord");
        }

        return data;
    }

    private void recordArchive() {
        double limits;
        try {
            limits = Double.parseDouble(sharedPreferences.getString("setting_pos_history", getResources().getString(R.string.history_expiration)));
        } catch (NumberFormatException e) {  // GOOD: The exception is caught.
            limits = 7;
        }
        final long weekSecond = (long) (limits * 24 * 60 * 60);

        try {
            mHistoryLocationDB.delete(DataBaseHistoryLocation.TABLE_NAME,
                    DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP + " < ?", new String[] {Long.toString(System.currentTimeMillis() / 1000 - weekSecond)});
        } catch (Exception e) {
            Log.e("HistoryActivity", "ERROR - recordArchive");
        }
    }

    private boolean deleteRecord(int ID) {
        boolean deleteRet = true;

        try {
            if (ID <= -1) {
                mHistoryLocationDB.delete(DataBaseHistoryLocation.TABLE_NAME,null, null);
            } else {
                mHistoryLocationDB.delete(DataBaseHistoryLocation.TABLE_NAME,
                        DataBaseHistoryLocation.DB_COLUMN_ID + " = ?", new String[] {Integer.toString(ID)});
            }
        } catch (Exception e) {
            deleteRet = false;
            Log.e("HistoryActivity", "ERROR - deleteRecord");
        }

        return deleteRet;
    }

    private void initSearchView() {
        SearchView mSearchView = findViewById(R.id.searchView);
        mSearchView.onActionViewExpanded();// 当展开无输入内容的时候，没有关闭的图标
        mSearchView.setSubmitButtonEnabled(false);//显示提交按钮
        mSearchView.setFocusable(false);
        mSearchView.clearFocus();
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {// 当点击搜索按钮时触发该方法
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {// 当搜索内容改变时触发该方法
                if (TextUtils.isEmpty(newText)) {
                    SimpleAdapter simAdapt = new SimpleAdapter(
                            HistoryActivity.this.getBaseContext(),
                            mAllRecord,
                            R.layout.history_item,
                            new String[]{KEY_ID, KEY_LOCATION, KEY_TIME, KEY_LNG_LAT_WGS, KEY_LNG_LAT_CUSTOM}, // 与下面数组元素要一一对应
                            new int[]{R.id.LocationID, R.id.LocationText, R.id.TimeText, R.id.WGSLatLngText, R.id.BDLatLngText});
                    mRecordListView.setAdapter(simAdapt);
                } else {
                    List<Map<String, Object>> searchRet = new ArrayList<>();
                    for (int i = 0; i < mAllRecord.size(); i++){
                        if (mAllRecord.get(i).toString().indexOf(newText) > 0){
                            searchRet.add(mAllRecord.get(i));
                        }
                    }
                    if (!searchRet.isEmpty()) {
                        SimpleAdapter simAdapt = new SimpleAdapter(
                                HistoryActivity.this.getBaseContext(),
                                searchRet,
                                R.layout.history_item,
                                new String[]{KEY_ID, KEY_LOCATION, KEY_TIME, KEY_LNG_LAT_WGS, KEY_LNG_LAT_CUSTOM}, // 与下面数组元素要一一对应
                                new int[]{R.id.LocationID, R.id.LocationText, R.id.TimeText, R.id.WGSLatLngText, R.id.BDLatLngText});
                        mRecordListView.setAdapter(simAdapt);
                    } else {
                        GoUtils.DisplayToast(HistoryActivity.this, getResources().getString(R.string.history_error_search));
                        SimpleAdapter simAdapt = new SimpleAdapter(
                                HistoryActivity.this.getBaseContext(),
                                mAllRecord,
                                R.layout.history_item,
                                new String[]{KEY_ID, KEY_LOCATION, KEY_TIME, KEY_LNG_LAT_WGS, KEY_LNG_LAT_CUSTOM}, // 与下面数组元素要一一对应
                                new int[]{R.id.LocationID, R.id.LocationText, R.id.TimeText, R.id.WGSLatLngText, R.id.BDLatLngText});
                        mRecordListView.setAdapter(simAdapt);
                    }
                }

                return false;
            }
        });
    }

    private void showDeleteDialog(String locID) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("警告");
        builder.setMessage("确定要删除该项历史记录吗?");
        builder.setPositiveButton("确定", (dialog, whichButton) -> {
            boolean deleteRet = deleteRecord(Integer.parseInt(locID));
            if (deleteRet) {
                GoUtils.DisplayToast(HistoryActivity.this, getResources().getString(R.string.history_delete_ok));
                updateRecordList();
            }
        });
        builder.setNegativeButton("取消", null);

        builder.show();
    }

    private void showInputDialog(String locID, String name) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(name);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("名称");
        builder.setView(input);
        builder.setPositiveButton("确认", (dialog, whichButton) -> {
            String userInput = input.getText().toString();
            DataBaseHistoryLocation.updateHistoryLocation(mHistoryLocationDB, locID, userInput);
            updateRecordList();
        });
        builder.setNegativeButton("取消", null);

        builder.show();
    }

    private String[] randomOffset(String longitude, String latitude) {
        String max_offset_default = getResources().getString(R.string.setting_random_offset_default);
        double lon_max_offset = Double.parseDouble(Objects.requireNonNull(sharedPreferences.getString("setting_lon_max_offset", max_offset_default)));
        double lat_max_offset = Double.parseDouble(Objects.requireNonNull(sharedPreferences.getString("setting_lat_max_offset", max_offset_default)));
        double lon = Double.parseDouble(longitude);
        double lat = Double.parseDouble(latitude);

        double randomLonOffset = (Math.random() * 2 - 1) * lon_max_offset;  // Longitude offset (meters)
        double randomLatOffset = (Math.random() * 2 - 1) * lat_max_offset;  // Latitude offset (meters)

        lon += randomLonOffset / 111320;    // (meters -> longitude)
        lat += randomLatOffset / 110574;    // (meters -> latitude)

        String offsetMessage = String.format(Locale.US, "经度偏移: %.2f米\n纬度偏移: %.2f米", randomLonOffset, randomLatOffset);
        GoUtils.DisplayToast(this, offsetMessage);

        return new String[]{String.valueOf(lon), String.valueOf(lat)};
    }

    private void initRecordListView() {
        noRecordText = findViewById(R.id.record_no_textview);
        mSearchLayout = findViewById(R.id.search_linear);
        mRecordListView = findViewById(R.id.record_list_view);
        mRecordListView.setOnItemClickListener((adapterView, view, i, l) -> {
            String bd09Longitude;
            String bd09Latitude;
            String name;
            name = (String) ((TextView) view.findViewById(R.id.LocationText)).getText();
            String bd09LatLng = (String) ((TextView) view.findViewById(R.id.BDLatLngText)).getText();
            bd09LatLng = bd09LatLng.substring(bd09LatLng.indexOf('[') + 1, bd09LatLng.indexOf(']'));
            String[] latLngStr = bd09LatLng.split(" ");
            bd09Longitude = latLngStr[0].substring(latLngStr[0].indexOf(':') + 1);
            bd09Latitude = latLngStr[1].substring(latLngStr[1].indexOf(':') + 1);

            // Random offset
            if(sharedPreferences.getBoolean("setting_random_offset", false)) {
                String[] offsetResult = randomOffset(bd09Longitude, bd09Latitude);
                bd09Longitude = offsetResult[0];
                bd09Latitude = offsetResult[1];
            }

            if (!MainActivity.showLocation(name, bd09Longitude, bd09Latitude)) {
                GoUtils.DisplayToast(this, getResources().getString(R.string.history_error_location));
            }
            this.finish();
        });

        mRecordListView.setOnItemLongClickListener((parent, view, position, id) -> {
            PopupMenu popupMenu = new PopupMenu(HistoryActivity.this, view);
            popupMenu.setGravity(Gravity.END | Gravity.BOTTOM);
            popupMenu.getMenu().add("编辑");
            popupMenu.getMenu().add("删除");

            popupMenu.setOnMenuItemClickListener(item -> {
                String locID = ((TextView) view.findViewById(R.id.LocationID)).getText().toString();
                String name = ((TextView) view.findViewById(R.id.LocationText)).getText().toString();
                return switch (Objects.requireNonNull(item.getTitle()).toString()) {
                    case "编辑" -> {
                        showInputDialog(locID, name);
                        yield true;
                    }
                    case "删除" -> {
                        showDeleteDialog(locID);
                        yield true;
                    }
                    default -> false;
                };
            });

            popupMenu.show();
            return true;
        });

        updateRecordList();
    }

    private void updateRecordList() {
        mAllRecord = fetchAllRecord();

        if (mAllRecord.isEmpty()) {
            mRecordListView.setVisibility(View.GONE);
            mSearchLayout.setVisibility(View.GONE);
            noRecordText.setVisibility(View.VISIBLE);
        } else {
            noRecordText.setVisibility(View.GONE);
            mRecordListView.setVisibility(View.VISIBLE);
            mSearchLayout.setVisibility(View.VISIBLE);

            try {
                SimpleAdapter simAdapt = new SimpleAdapter(
                        this,
                        mAllRecord,
                        R.layout.history_item,
                        new String[]{KEY_ID, KEY_LOCATION, KEY_TIME, KEY_LNG_LAT_WGS, KEY_LNG_LAT_CUSTOM},
                        new int[]{R.id.LocationID, R.id.LocationText, R.id.TimeText, R.id.WGSLatLngText, R.id.BDLatLngText});
                mRecordListView.setAdapter(simAdapt);
            } catch (Exception e) {
                Log.e("HistoryActivity", "ERROR - updateRecordList");
            }
        }
    }
}