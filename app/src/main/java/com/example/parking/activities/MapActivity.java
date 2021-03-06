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
    ArrayList<Document> documentArrayList = new ArrayList<>(); //????????? ?????? ?????? ?????????
    MapPOIItem searchMarker = new MapPOIItem();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_map);

        onCheckPermission();

        bus.register(this); //????????? ??????
    }

    // ?????? ?????? ??????
    public void onCheckPermission(){
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(this, "??? ????????? ????????? ??????????????? ???????????? ?????????.", Toast.LENGTH_LONG).show();
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
                    Toast.makeText(this, "??? ????????? ?????? ?????? ????????? ?????????????????????.", Toast.LENGTH_LONG).show();
                    initView();
                }
                else{
                    Toast.makeText(this, "??? ????????? ?????? ?????? ????????? ???????????????.", Toast.LENGTH_LONG).show();
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
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false); //????????????????????? ??????
        recyclerView.addItemDecoration(new DividerItemDecoration(getApplicationContext(), DividerItemDecoration.VERTICAL)); //??????????????? ??????
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(locationAdapter);

        //??? ?????????
        mMapView.setMapViewEventListener(this); // this??? MapView.MapViewEventListener ??????.
        mMapView.setPOIItemEventListener(this);
        mMapView.setOpenAPIKeyAuthenticationResultListener(this);

        //???????????????
        fab.setOnClickListener(this);
        fab1.setOnClickListener(this);

        Toast.makeText(this, "?????? ??????????????????", Toast.LENGTH_SHORT).show();

        //??? ????????? (???????????? ????????????)
        mMapView.setCurrentLocationEventListener(this);
        mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading);
        mLoaderLayout.setVisibility(View.VISIBLE);



        // editText ?????? ??????????????????
        mSearchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
                // ???????????? ??????
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
                // ????????? ????????? ???
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
                FancyToast.makeText(getApplicationContext(), "????????????????????? ????????? ??????????????????", FancyToast.LENGTH_SHORT, FancyToast.DEFAULT, false).show();
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
                FancyToast.makeText(this, "??????????????? ??????", FancyToast.LENGTH_SHORT, FancyToast.DEFAULT, false).show();
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
                            //??????????????? ????????? new MapPoint()???  ????????????. ??????????????? ???????????? ????????? ???????????? ???????????????
                            MapPoint mapPoint = MapPoint.mapPointWithGeoCoord(x, y);
                            marker.setMapPoint(mapPoint);
                            marker.setMarkerType(MapPOIItem.MarkerType.CustomImage); // ??????????????? ????????? ????????? ??????.
                            marker.setCustomImageResourceId(R.drawable.ic_parking); // ?????? ?????????.
                            marker.setCustomImageAutoscale(false); // hdpi, xhdpi ??? ??????????????? ???????????? ???????????? ????????? ?????? ?????? ?????????????????? ????????? ????????? ??????.
                            marker.setCustomImageAnchor(0.5f, 1.0f); // ?????? ???????????? ????????? ?????? ??????(???????????????) ?????? - ?????? ????????? ?????? ?????? ?????? x(0.0f ~ 1.0f), y(0.0f ~ 1.0f) ???.
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

    //??? ?????? ????????? ??????
    @Override
    public void onMapViewSingleTapped(MapView mapView, MapPoint mapPoint) {
        //???????????????????????? ??????????????? ????????? ???????????????
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

    //?????????(POLLITEM) ????????? ??????
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
                FancyToast.makeText(getApplicationContext(), "??????????????? ?????? ??????????????? ????????????.", FancyToast.LENGTH_SHORT, FancyToast.DEFAULT, false).show();
                mLoaderLayout.setVisibility(View.GONE);
                Intent intent = new Intent(MapActivity.this, PlaceDetailActivity.class);
                startActivity(intent);
            }
        });

    }

    // ?????? ?????????????????? ??????
    @Override
    public void onDraggablePOIItemMoved(MapView mapView, MapPOIItem mapPOIItem, MapPoint mapPoint) {
        MapPoint.GeoCoordinate mapPointGeo = mapPoint.getMapPointGeoCoord();
        mSearchName = "???????????? ??????";
        mSearchLng = mapPointGeo.longitude;
        mSearchLat = mapPointGeo.latitude;
        mMapView.setMapCenterPoint(MapPoint.mapPointWithGeoCoord(mSearchLat, mSearchLng), true);
        searchMarker.setItemName(mSearchName);
        MapPoint mapPoint2 = MapPoint.mapPointWithGeoCoord(mSearchLat, mSearchLng);
        searchMarker.setMapPoint(mapPoint2);
        searchMarker.setMarkerType(MapPOIItem.MarkerType.BluePin); // ???????????? ???????????? BluePin ?????? ??????.
        searchMarker.setSelectedMarkerType(MapPOIItem.MarkerType.RedPin); // ????????? ???????????????, ???????????? ???????????? RedPin ?????? ??????.
        searchMarker.setDraggable(true);
        mMapView.addPOIItem(searchMarker);
    }

    /*
     *  ?????? ?????? ????????????(setCurrentLocationEventListener)
     */
    @Override
    public void onCurrentLocationUpdate(MapView mapView, MapPoint mapPoint, float accuracyInMeters) {
        MapPoint.GeoCoordinate mapPointGeo = mapPoint.getMapPointGeoCoord();
        Log.i(TAG, String.format("MapView onCurrentLocationUpdate (%f,%f) accuracy (%f)", mapPointGeo.latitude, mapPointGeo.longitude, accuracyInMeters));
        currentMapPoint = MapPoint.mapPointWithGeoCoord(mapPointGeo.latitude, mapPointGeo.longitude);
        //??? ????????? ?????? ?????? ??????
        mMapView.setMapCenterPoint(currentMapPoint, true);
        //??????????????? ?????? ?????? ??????
        mCurrentLat = mapPointGeo.latitude;
        mCurrentLng = mapPointGeo.longitude;
        Log.d(TAG, "???????????? => " + mCurrentLat + "  " + mCurrentLng);
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

    @Subscribe //???????????? ????????? ????????? ????????????
    public void search(Document document) {//public?????? ???????????????
        FancyToast.makeText(getApplicationContext(), document.getPlaceName() + " ??????", FancyToast.LENGTH_SHORT, FancyToast.DEFAULT, false).show();
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
        searchMarker.setMarkerType(MapPOIItem.MarkerType.BluePin); // ???????????? ???????????? BluePin ?????? ??????.
        searchMarker.setSelectedMarkerType(MapPOIItem.MarkerType.RedPin); // ????????? ???????????????, ???????????? ???????????? RedPin ?????? ??????.
        //?????? ????????? ???????????? ??????
        searchMarker.setDraggable(true);
        mMapView.addPOIItem(searchMarker);
    }


    @Override
    public void finish() {
        super.finish();
        bus.unregister(this); //??????????????? ????????? ????????? ????????????
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
