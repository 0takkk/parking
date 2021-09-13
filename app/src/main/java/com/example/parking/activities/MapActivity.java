package com.example.parking.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.example.parking.R;
import com.example.parking.adapter.LocationAdapter;
import com.example.parking.api.ApiClient;
import com.example.parking.api.ApiInterface;
import com.example.parking.model.category_search.Document;
import com.example.parking.model.category_search.CategoryResult;
import com.example.parking.utils.BusProvider;
import com.example.parking.utils.IntentKey;
import com.shashank.sony.fancytoastlib.FancyToast;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;


import net.daum.mf.map.api.MapCircle;
import net.daum.mf.map.api.MapPOIItem;
import net.daum.mf.map.api.MapPoint;
import net.daum.mf.map.api.MapView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class MapActivity extends AppCompatActivity implements MapView.MapViewEventListener, MapView.POIItemEventListener, MapView.OpenAPIKeyAuthenticationResultListener, View.OnClickListener, MapView.CurrentLocationEventListener {
    final static String TAG = "MapTAG";
    //xml
    int nCurrentPermission = 0;
    static final int PERMISSION_REQUEST = 0x0000001;
    MapView mMapView;
    ViewGroup mMapViewContainer;
    EditText mSearchEdit;
    private Boolean isFabOpen = false;
    private FloatingActionButton fab, fab1, searchDetailFab, stopTrackingFab;
    RelativeLayout mLoaderLayout;
    RecyclerView recyclerView;

    //value
    MapPoint currentMapPoint;
    private double mCurrentLng; //Long = X, Lat = Y
    private double mCurrentLat;
    private double mSearchLng = -1;
    private double mSearchLat = -1;
    private String mSearchName;
    boolean isTrackingMode = false;
    Bus bus = BusProvider.getInstance();

    ArrayList<Document> parkList = new ArrayList<>();
    ArrayList<Document> documentArrayList = new ArrayList<>(); //지역명 검색 결과 리스트
    MapPOIItem searchMarker = new MapPOIItem();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_map);

        onCheckPermission();

        bus.register(this); //정류소 등록
    }

    // 위치 권한 요청
    public void onCheckPermission(){
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(this, "앱 실행을 위해서 위치권한을 설정해야 합니다.", Toast.LENGTH_LONG).show();
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                        PERMISSION_REQUEST);
            }
            else{
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                        PERMISSION_REQUEST);
            }
        }
        else{
            initView();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
        switch(requestCode){
            case PERMISSION_REQUEST :
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    Toast.makeText(this, "앱 실행을 위한 위치 권한이 설정되었습니다.", Toast.LENGTH_LONG).show();
                    initView();
                }
                else{
                    Toast.makeText(this, "앱 실행을 위한 위치 권한이 필요합니다.", Toast.LENGTH_LONG).show();
                    finish();
                }

                break;
        }
    }

    private void initView() {
        //binding
        mSearchEdit = findViewById(R.id.map_et_search);
        fab = findViewById(R.id.fab);
        fab1 = findViewById(R.id.fab1);
        mLoaderLayout = findViewById(R.id.loaderLayout);
        mMapView = new MapView(this);
        mMapViewContainer = findViewById(R.id.map_mv_mapcontainer);
        mMapViewContainer.addView(mMapView);
        recyclerView = findViewById(R.id.map_recyclerview);
        LocationAdapter locationAdapter = new LocationAdapter(documentArrayList, getApplicationContext(), mSearchEdit, recyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false); //레이아웃매니저 생성
        recyclerView.addItemDecoration(new DividerItemDecoration(getApplicationContext(), DividerItemDecoration.VERTICAL)); //아래구분선 세팅
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(locationAdapter);

        //맵 리스너
        mMapView.setMapViewEventListener(this); // this에 MapView.MapViewEventListener 구현.
        mMapView.setPOIItemEventListener(this);
        mMapView.setOpenAPIKeyAuthenticationResultListener(this);

        //버튼리스너
        fab.setOnClickListener(this);
        fab1.setOnClickListener(this);

        Toast.makeText(this, "맵을 로딩중입니다", Toast.LENGTH_SHORT).show();

        //맵 리스너 (현재위치 업데이트)
        mMapView.setCurrentLocationEventListener(this);
        mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading);
        mLoaderLayout.setVisibility(View.VISIBLE);



        // editText 검색 텍스처이벤트
        mSearchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
                // 입력하기 전에
                recyclerView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                if (charSequence.length() >= 1) {
                    documentArrayList.clear();
                    locationAdapter.clear();
                    locationAdapter.notifyDataSetChanged();
                    ApiInterface apiInterface = ApiClient.getApiClient().create(ApiInterface.class);
                    Call<CategoryResult> call = apiInterface.getSearchLocation(getString(R.string.restapi_key), charSequence.toString(), 15);
                    call.enqueue(new Callback<CategoryResult>() {
                        @Override
                        public void onResponse(@NotNull Call<CategoryResult> call, @NotNull Response<CategoryResult> response) {
                            if (response.isSuccessful()) {
                                assert response.body() != null;
                                for (Document document : response.body().getDocuments()) {
                                    locationAdapter.addItem(document);
                                }
                                locationAdapter.notifyDataSetChanged();
                            }
                        }

                        @Override
                        public void onFailure(@NotNull Call<CategoryResult> call, @NotNull Throwable t) {

                        }
                    });
                } else {
                    if (charSequence.length() <= 0) {
                        recyclerView.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
                // 입력이 끝났을 때
            }
        });

        mSearchEdit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus) {
                } else {
                    recyclerView.setVisibility(View.GONE);
                }
            }
        });
        mSearchEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FancyToast.makeText(getApplicationContext(), "검색리스트에서 장소를 선택해주세요", FancyToast.LENGTH_SHORT, FancyToast.DEFAULT, false).show();
            }
        });
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.fab:
                isTrackingMode = false;
                //mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOff);
                if (mSearchLat != -1 && mSearchLng != -1) {
                    mMapView.removeAllPOIItems();
                    mMapView.removeAllCircles();
                    mMapView.addPOIItem(searchMarker);
                    MapPoint searchMapPoint = MapPoint.mapPointWithGeoCoord(mSearchLat, mSearchLng);
                    mMapView.setMapCenterPoint(searchMapPoint, true);
                    requestSearchLocal(mSearchLng, mSearchLat);
                } else {
                    mMapView.removeAllPOIItems();
                    mMapView.removeAllCircles();
                    requestSearchLocal(mCurrentLng, mCurrentLat);
                    mMapView.setMapCenterPoint(currentMapPoint, true);
                    mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading);
                }
                mMapView.setZoomLevel(2, true);
                mLoaderLayout.setVisibility(View.GONE);
                break;
            case R.id.fab1:
                isTrackingMode = false;
                FancyToast.makeText(this, "현재위치로 이동", FancyToast.LENGTH_SHORT, FancyToast.DEFAULT, false).show();
                mSearchLat = -1;
                mSearchLng = -1;
                mSearchEdit.setText("");
                //searchDetailFab.setVisibility(View.GONE);
                mLoaderLayout.setVisibility(View.VISIBLE);

                mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading);
                mMapView.setZoomLevel(2, true);
                //stopTrackingFab.setVisibility(View.VISIBLE);
                mLoaderLayout.setVisibility(View.GONE);
                break;
        }
    }

    private void requestSearchLocal(double x, double y) {
        parkList.clear();
        ApiInterface apiInterface = ApiClient.getApiClient().create(ApiInterface.class);
        Call<CategoryResult> call = apiInterface.getSearchCategory(getString(R.string.restapi_key), "PK6", x + "", y + "", 500);
        call.enqueue(new Callback<CategoryResult>() {
            @Override
            public void onResponse(@NotNull Call<CategoryResult> call, @NotNull Response<CategoryResult> response) {
                if (response.isSuccessful()) {
                    assert response.body() != null;
                    if (response.body().getDocuments() != null) {
                        Log.d(TAG, "parkList Success");
                        parkList.addAll(response.body().getDocuments());

                        MapCircle circle1 = new MapCircle(
                                MapPoint.mapPointWithGeoCoord(y, x), // center
                                500, // radius
                                Color.argb(128, 255, 0, 0), // strokeColor
                                Color.argb(50, 240, 100, 150) // fillColor
                        );
                        circle1.setTag(5678);
                        mMapView.addCircle(circle1);
                        Log.d("SIZE1", parkList.size() + "");
                        int tagNum = 10;
                        for (Document document : parkList) {
                            MapPOIItem marker = new MapPOIItem();
                            marker.setItemName(document.getPlaceName());
                            marker.setTag(tagNum++);
                            double x = Double.parseDouble(document.getY());
                            double y = Double.parseDouble(document.getX());
                            //카카오맵은 참고로 new MapPoint()로  생성못함. 좌표기준이 여러개라 이렇게 메소드로 생성해야함
                            MapPoint mapPoint = MapPoint.mapPointWithGeoCoord(x, y);
                            marker.setMapPoint(mapPoint);
                            marker.setMarkerType(MapPOIItem.MarkerType.CustomImage); // 마커타입을 커스텀 마커로 지정.
                            marker.setCustomImageResourceId(R.drawable.ic_parking); // 마커 이미지.
                            marker.setCustomImageAutoscale(false); // hdpi, xhdpi 등 안드로이드 플랫폼의 스케일을 사용할 경우 지도 라이브러리의 스케일 기능을 꺼줌.
                            marker.setCustomImageAnchor(0.5f, 1.0f); // 마커 이미지중 기준이 되는 위치(앵커포인트) 지정 - 마커 이미지 좌측 상단 기준 x(0.0f ~ 1.0f), y(0.0f ~ 1.0f) 값.
                            mMapView.addPOIItem(marker);
                        }
                    }
                }
            }

            @Override
            public void onFailure(@NotNull Call<CategoryResult> call, @NotNull Throwable t) {
                Log.d(TAG, "FAIL");
            }
        });
    }

    @Override
    public void onMapViewInitialized(MapView mapView) {
    }

    @Override
    public void onMapViewCenterPointMoved(MapView mapView, MapPoint mapPoint) {
    }

    @Override
    public void onMapViewZoomLevelChanged(MapView mapView, int i) {
    }

    //맵 한번 클릭시 호출
    @Override
    public void onMapViewSingleTapped(MapView mapView, MapPoint mapPoint) {
        //검색창켜져있을때 맵클릭하면 검색창 사라지게함
        recyclerView.setVisibility(View.GONE);
    }

    @Override
    public void onMapViewDoubleTapped(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewLongPressed(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewDragStarted(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewDragEnded(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewMoveFinished(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onDaumMapOpenAPIKeyAuthenticationResult(MapView mapView, int i, String s) {

    }

    @Override
    public void onPOIItemSelected(MapView mapView, MapPOIItem mapPOIItem) {

    }



    @Override
    public void onCalloutBalloonOfPOIItemTouched(MapView mapView, MapPOIItem mapPOIItem) {
    }

    //말풍선(POLLITEM) 클릭시 호출
    @Override
    public void onCalloutBalloonOfPOIItemTouched(MapView mapView, MapPOIItem mapPOIItem, MapPOIItem.CalloutBalloonButtonType calloutBalloonButtonType) {
        double lat = mapPOIItem.getMapPoint().getMapPointGeoCoord().latitude;
        double lng = mapPOIItem.getMapPoint().getMapPointGeoCoord().longitude;
        Toast.makeText(this, mapPOIItem.getItemName(), Toast.LENGTH_SHORT).show();
        mLoaderLayout.setVisibility(View.VISIBLE);
        ApiInterface apiInterface = ApiClient.getApiClient().create(ApiInterface.class);
        Call<CategoryResult> call = apiInterface.getSearchLocationDetail(getString(R.string.restapi_key), mapPOIItem.getItemName(), String.valueOf(lat), String.valueOf(lng), 1);
        call.enqueue(new Callback<CategoryResult>() {
            @Override
            public void onResponse(@NotNull Call<CategoryResult> call, @NotNull Response<CategoryResult> response) {
                mLoaderLayout.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    Intent intent = new Intent(MapActivity.this, PlaceDetailActivity.class);
                    assert response.body() != null;
                    intent.putExtra(IntentKey.PLACE_SEARCH_DETAIL_EXTRA, response.body().getDocuments().get(0));
                    intent.putExtra("mCurrentLat", mCurrentLat);
                    intent.putExtra("mCurrentLng",mCurrentLng);
                    intent.putExtra("lat",lat);
                    intent.putExtra("lng",lng);
                    startActivity(intent);
                }
            }

            @Override
            public void onFailure(Call<CategoryResult> call, Throwable t) {
                FancyToast.makeText(getApplicationContext(), "해당장소에 대한 상세정보는 없습니다.", FancyToast.LENGTH_SHORT, FancyToast.DEFAULT, false).show();
                mLoaderLayout.setVisibility(View.GONE);
                Intent intent = new Intent(MapActivity.this, PlaceDetailActivity.class);
                startActivity(intent);
            }
        });

    }

    // 마커 드래그이동시 호출
    @Override
    public void onDraggablePOIItemMoved(MapView mapView, MapPOIItem mapPOIItem, MapPoint mapPoint) {
        MapPoint.GeoCoordinate mapPointGeo = mapPoint.getMapPointGeoCoord();
        mSearchName = "드래그한 장소";
        mSearchLng = mapPointGeo.longitude;
        mSearchLat = mapPointGeo.latitude;
        mMapView.setMapCenterPoint(MapPoint.mapPointWithGeoCoord(mSearchLat, mSearchLng), true);
        searchMarker.setItemName(mSearchName);
        MapPoint mapPoint2 = MapPoint.mapPointWithGeoCoord(mSearchLat, mSearchLng);
        searchMarker.setMapPoint(mapPoint2);
        searchMarker.setMarkerType(MapPOIItem.MarkerType.BluePin); // 기본으로 제공하는 BluePin 마커 모양.
        searchMarker.setSelectedMarkerType(MapPOIItem.MarkerType.RedPin); // 마커를 클릭했을때, 기본으로 제공하는 RedPin 마커 모양.
        searchMarker.setDraggable(true);
        mMapView.addPOIItem(searchMarker);
    }

    /*
     *  현재 위치 업데이트(setCurrentLocationEventListener)
     */
    @Override
    public void onCurrentLocationUpdate(MapView mapView, MapPoint mapPoint, float accuracyInMeters) {
        MapPoint.GeoCoordinate mapPointGeo = mapPoint.getMapPointGeoCoord();
        Log.i(TAG, String.format("MapView onCurrentLocationUpdate (%f,%f) accuracy (%f)", mapPointGeo.latitude, mapPointGeo.longitude, accuracyInMeters));
        currentMapPoint = MapPoint.mapPointWithGeoCoord(mapPointGeo.latitude, mapPointGeo.longitude);
        //이 좌표로 지도 중심 이동
        mMapView.setMapCenterPoint(currentMapPoint, true);
        //전역변수로 현재 좌표 저장
        mCurrentLat = mapPointGeo.latitude;
        mCurrentLng = mapPointGeo.longitude;
        Log.d(TAG, "현재위치 => " + mCurrentLat + "  " + mCurrentLng);
        mLoaderLayout.setVisibility(View.GONE);
        if (!isTrackingMode) {
            mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOff);
        }
    }

    @Override
    public void onCurrentLocationDeviceHeadingUpdate(MapView mapView, float v) {

    }

    @Override
    public void onCurrentLocationUpdateFailed(MapView mapView) {
        Log.i(TAG, "onCurrentLocationUpdateFailed");
        mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading);
    }

    @Override
    public void onCurrentLocationUpdateCancelled(MapView mapView) {
        Log.i(TAG, "onCurrentLocationUpdateCancelled");
        mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading);
    }

    @Subscribe //검색예시 클릭시 이벤트 오토버스
    public void search(Document document) {//public항상 붙여줘야함
        FancyToast.makeText(getApplicationContext(), document.getPlaceName() + " 검색", FancyToast.LENGTH_SHORT, FancyToast.DEFAULT, false).show();
        mSearchName = document.getPlaceName();
        mSearchLng = Double.parseDouble(document.getX());
        mSearchLat = Double.parseDouble(document.getY());
        mMapView.setMapCenterPoint(MapPoint.mapPointWithGeoCoord(mSearchLat, mSearchLng), true);
        mMapView.removePOIItem(searchMarker);
        mMapView.setZoomLevel(2, true);
        searchMarker.setItemName(mSearchName);
        searchMarker.setTag(10000);
        MapPoint mapPoint = MapPoint.mapPointWithGeoCoord(mSearchLat, mSearchLng);
        searchMarker.setMapPoint(mapPoint);
        searchMarker.setMarkerType(MapPOIItem.MarkerType.BluePin); // 기본으로 제공하는 BluePin 마커 모양.
        searchMarker.setSelectedMarkerType(MapPOIItem.MarkerType.RedPin); // 마커를 클릭했을때, 기본으로 제공하는 RedPin 마커 모양.
        //마커 드래그 가능하게 설정
        searchMarker.setDraggable(true);
        mMapView.addPOIItem(searchMarker);
    }


    @Override
    public void finish() {
        super.finish();
        bus.unregister(this); //이액티비티 떠나면 정류소 해제해줌
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOff);
        mMapView.setShowCurrentLocationMarker(false);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
