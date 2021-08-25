package com.example.parking.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.example.parking.R;
import com.example.parking.model.category_search.Document;
import com.example.parking.utils.IntentKey;
import com.shashank.sony.fancytoastlib.FancyToast;

import org.jetbrains.annotations.NotNull;

public class PlaceDetailActivity extends AppCompatActivity {
    TextView placeNameText;
    TextView addressText;
    TextView urlText;
    TextView areaText;
    double mCurrentLat, mCurrentLng, lat, lng;
    String placename;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place_detail);
        placeNameText = findViewById(R.id.placedetail_tv_name);
        addressText = findViewById(R.id.placedetail_tv_address);
        urlText = findViewById(R.id.placedetail_tv_url);
        areaText = findViewById(R.id.placedetail_tv_area);
        processIntent();

        Button b = (Button)findViewById(R.id.find);
        b.setOnClickListener(new Button.OnClickListener(){
            public void onClick(View v){
                showMap(Uri.parse("daummaps://route?sp=" + mCurrentLat + "," + mCurrentLng + "&ep=" + lat + "," + lng + "&by=CAR"));
            }
        });
    }


    private void processIntent(){
        Intent processIntent = getIntent();
        Document document = processIntent.getParcelableExtra(IntentKey.PLACE_SEARCH_DETAIL_EXTRA);
        placename = document.getPlaceName();
        placeNameText.setText(placename);
        addressText.setText(document.getAddressName());
        urlText.setText(document.getPlaceUrl());
        mCurrentLat = processIntent.getDoubleExtra("mCurrentLat", 0);
        mCurrentLng = processIntent.getDoubleExtra("mCurrentLng", 0);
        lat = processIntent.getDoubleExtra("lat", 0);
        lng = processIntent.getDoubleExtra("lng", 0);

        DatabaseReference rootDatabaseref = FirebaseDatabase.getInstance().getReference();
        DatabaseReference parkingRef = rootDatabaseref.child("Park");
        DatabaseReference areaRef = parkingRef.child(placename);


        areaRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull @NotNull DataSnapshot snapshot) {
                String max="none", cur="none";

                for(DataSnapshot snap : snapshot.getChildren()){
                    String key = snap.getKey();
                    if(key.equals("MAX")) max = snap.getValue().toString();
                    if(key.equals("cur")) cur = snap.getValue().toString();
                }
                areaText.setText(cur + " / " + max);
            }

            @Override
            public void onCancelled(@NonNull @NotNull DatabaseError error) {

            }
        });
    }

    // 길찾기 카카오맵 호출( 카카오맵앱이 없을 경우 플레이스토어 링크로 이동)
    public void showMap(Uri geoLocation) {
        Intent intent;
        try {
            FancyToast.makeText(getApplicationContext(), "카카오맵으로 길찾기를 시도합니다.", FancyToast.LENGTH_SHORT, FancyToast.INFO, true).show();
            intent = new Intent(Intent.ACTION_VIEW, geoLocation);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            FancyToast.makeText(getApplicationContext(), "길찾기에는 카카오맵이 필요합니다. 다운받아주시길 바랍니다.", FancyToast.LENGTH_SHORT, FancyToast.INFO, true).show();
            intent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse("https://play.google.com/store/apps/details?id=net.daum.android.map&hl=ko"));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

}
