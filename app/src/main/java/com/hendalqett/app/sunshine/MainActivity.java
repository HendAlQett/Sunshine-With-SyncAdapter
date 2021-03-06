package com.hendalqett.app.sunshine;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.util.Pair;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.hendalqett.app.sunshine.gcm.RegistrationIntentService;
import com.hendalqett.app.sunshine.sync.SunshineSyncAdapter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;


public class MainActivity extends AppCompatActivity implements ForecastFragment.Callback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,ForecastFragment.TodayDataCallback {


    final String LOG_TAG = MainActivity.class.getSimpleName();
    String mLocation;

    //private final String FORECASTFRAGMENT_TAG = "FFTAG";
    private static final String DETAILFRAGMENT_TAG = "DFTAG";
    boolean mTwoPane;
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    public static final String SENT_TOKEN_TO_SERVER = "sentTokenToServer";
    private static final String WEATHER_MAX_KEY = "weather_max";
    private static final String WEATHER_MIN_KEY = "weather_min";

    private static final String WEATHER_IMAGE_KEY = "weather_photo";
    private boolean mResolvingError = false;

    private GoogleApiClient mGoogleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        mLocation = Utility.getPreferredLocation(this);


        if (findViewById(R.id.weather_detail_container) != null) {
            // The detail container view will be present only in the large-screen layouts
            // (res/layout-sw600dp). If this view is present, then the activity should be
            // in two-pane mode.
            mTwoPane = true;
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            if (savedInstanceState == null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.weather_detail_container, new DetailFragment(), DETAILFRAGMENT_TAG)
                        .commit();
            }
            //I don't need to inflate the fragment again if savedInstanceState in not null because the system saved the fragment on orientation.
        } else {
            mTwoPane = false;
            //To remove the shadow under the ActionBar
            getSupportActionBar().setElevation(0f);
        }

        ForecastFragment forecastFragment = ((ForecastFragment) getSupportFragmentManager()
                .findFragmentById(R.id.fragment_forecast));
        forecastFragment.setUseTodayLayout(!mTwoPane);


        SunshineSyncAdapter.initializeSyncAdapter(this);
        if (checkPlayServices()) {
            // This is where we could either prompt a user that they should install
            // the latest version of Google Play Services, or add an error snackbar
            // that some features won't be available.

            // Because this is the initial creation of the app, we'll want to be certain we have
            // a token. If we do not, then we will start the IntentService that will register this
            // application with GCM.
            SharedPreferences sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(this);
            boolean sentToken = sharedPreferences.getBoolean(SENT_TOKEN_TO_SERVER, false);
            if (!sentToken) {
                Intent intent = new Intent(this, RegistrationIntentService.class);
                startService(intent);
            }

        }

    }

    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(LOG_TAG, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }


    @Override
    protected void onStart() {
        super.onStart();
        Log.d(LOG_TAG, "onStart");
        if (!mResolvingError) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "onResume");

//        String location = Utility.getPreferredLocation(this);
//        // update the location in our second pane using the fragment manager
//        if (location != null && !location.equals(mLocation)) {
//            ForecastFragment ff = (ForecastFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_forecast);
//            if (null != ff) {
//                ff.onLocationChanged();
//            }
//            mLocation = location;
//        }


        String location = Utility.getPreferredLocation(this);
        // update the location in our second pane using the fragment manager
        if (location != null && !location.equals(mLocation)) {
            ForecastFragment ff = (ForecastFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_forecast);
            if (null != ff) {
                ff.onLocationChanged();
            }
            DetailFragment df = (DetailFragment) getSupportFragmentManager().findFragmentByTag(DETAILFRAGMENT_TAG);
            if (null != df) {
                df.onLocationChanged(location);
            }
            mLocation = location;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!mResolvingError && (mGoogleApiClient != null) && (mGoogleApiClient.isConnected())) {
//            Wearable.DataApi.removeListener(mGoogleApiClient, this);
//            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
//            Wearable.CapabilityApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
        Log.d(LOG_TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(LOG_TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }


        return super.onOptionsItemSelected(item);
    }


    public void showMap(Uri geoLocation) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(geoLocation);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    @Override
    public void onItemSelected(Uri contentUri, ForecastAdapter.ForecastAdapterViewHolder vh) {

        if (mTwoPane) {
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            Bundle args = new Bundle();
            args.putParcelable(DetailFragment.DETAIL_URI, contentUri);
            DetailFragment fragment = new DetailFragment();
            fragment.setArguments(args);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.weather_detail_container, fragment, DETAILFRAGMENT_TAG)
                    .commit();
        } else {
            Intent intent = new Intent(this, DetailActivity.class)
                    .setData(contentUri);
//            startActivity(intent);

            ActivityOptionsCompat activityOptions = ActivityOptionsCompat.makeSceneTransitionAnimation(this,
                    new Pair<View, String>(vh.mIconView, getString(R.string.detail_icon_transition_name)));
            ActivityCompat.startActivity(this, intent, activityOptions.toBundle());
        }


    }

    @Override
    public void onConnected(Bundle bundle) {


    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

//    public void sendWeatherData() {
//        PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/weather");
//        putDataMapReq.getDataMap().putInt(WEATHER_MAX_KEY, 4445);
//        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
//        PendingResult<DataApi.DataItemResult> pendingResult =
//                Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
//    }

    private static Asset toAsset(Bitmap bitmap) {
        ByteArrayOutputStream byteStream = null;
        try {
            byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
            return Asset.createFromBytes(byteStream.toByteArray());
        } finally {
            if (null != byteStream) {
                try {
                    byteStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

//    private void sendPhoto(Asset asset) {
//        PutDataMapRequest dataMap = PutDataMapRequest.create(IMAGE_PATH);
////        dataMap.getDataMap().putAsset(IMAGE_KEY, asset);
//        dataMap.getDataMap().putInt(WEATHER_MAX_KEY, 4445);
//        PutDataRequest request = dataMap.asPutDataRequest();
//        request.setUrgent();
//
//        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
//                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
//                    @Override
//                    public void onResult(DataApi.DataItemResult dataItemResult) {
//                        Log.d("DataSent", "Sending image was successful: " + dataItemResult.getStatus().isSuccess());
//                    }
//                });
//    }

    private void sendWeather(String max, String min, Asset asset) {
        PutDataMapRequest putDataMap = PutDataMapRequest.create("/com.hend.weather");
        putDataMap.getDataMap().putAsset(WEATHER_IMAGE_KEY, asset);
        putDataMap.getDataMap().putString(WEATHER_MAX_KEY, max);
        putDataMap.getDataMap().putDouble("Time", System.currentTimeMillis());
        putDataMap.getDataMap().putString(WEATHER_MIN_KEY,min);
        PutDataRequest request = putDataMap.asPutDataRequest();
        request.setUrgent();

        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (dataItemResult.getStatus().isSuccess()) {
                            Log.d("DataSent", "Sending Time was successful: ");
                        }
                    }
                });
    }


    @Override
    public void onTodayDataLoaded(String max, String min, Bitmap bitmap) {
        Asset asset = toAsset(bitmap);
        sendWeather(max,min,asset);
    }
}
