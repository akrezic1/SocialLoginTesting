package hr.akrezic.sociallogin;

import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.model.people.Person;
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;


public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        View.OnClickListener {

    /* Request code used to invoke sign in user interactions. */
    private static final int RC_SIGN_IN = 0;

    /* Client used to interact with Google APIs. */
    private GoogleApiClient mGoogleApiClient;

    /* A flag indicating that a PendingIntent is in progress and prevents
     * us from starting further intents.
     */
    private boolean mIntentInProgress;

    /**
     * True if the sign-in button was clicked.  When true, we know to resolve all
     * issues preventing sign-in without waiting.
     */
    private boolean mSignInClicked;

    private AccessToken fbAccessToken;

    private ImageView    profileImage;
    private TextView     profileName;
    private LoginButton  fbLogin;
    private SignInButton googleLogin;

    CallbackManager callbackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Init fb
        FacebookSdk.sdkInitialize(getApplicationContext());
        callbackManager = CallbackManager.Factory.create();

        setContentView(R.layout.activity_main);


        googleLogin  = (SignInButton) findViewById(R.id.sign_in_google);
        profileImage = (ImageView)    findViewById(R.id.image_profile);
        profileName  = (TextView)     findViewById(R.id.text_profile_name);
        fbLogin      = (LoginButton)  findViewById(R.id.sign_in_facebook);

        googleLogin.setOnClickListener(this);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Plus.API)
                .addScope(Plus.SCOPE_PLUS_LOGIN)
                .build();

        fbLogin.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                // save fb access token
                fbAccessToken = loginResult.getAccessToken();
                Toast.makeText(getApplicationContext(), fbAccessToken.getToken(), Toast.LENGTH_LONG).show();

                // set profile picture and name on screen
                String userId = loginResult.getAccessToken().getUserId();
                setFbProfilePicture(userId);
                setFbProfileName();
            }

            @Override
            public void onCancel() {

            }

            @Override
            public void onError(FacebookException e) {
                Toast.makeText(getApplicationContext(), "Jebiga brate", Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Set profile image from fb to ImageView for given user id
     * @param userId fb users id
     */
    private void setFbProfilePicture(String userId) {
        String fbProfilePic = "https://graph.facebook.com/" + userId + "/picture?type=large";
        Picasso.with(getApplicationContext()).load(fbProfilePic).into(profileImage);
    }

    /**
     * Set profile name from fb to TextView
     */
    private void setFbProfileName() {
        GraphRequest request = GraphRequest.newMeRequest(fbAccessToken, new GraphRequest.GraphJSONObjectCallback() {
            @Override
            public void onCompleted(JSONObject jsonObject, GraphResponse graphResponse) {
                try {
                    profileName.setText(jsonObject.getString("name"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        // set Graph parameters to get from fb
        Bundle parameters = new Bundle();
        parameters.putString("fields", "id,name");

        request.setParameters(parameters);
        request.executeAsync();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_SIGN_IN) {
            if (resultCode != RESULT_OK) {
                mSignInClicked = false;
            }

            mIntentInProgress = false;

            if (!mGoogleApiClient.isConnected()) {
                mGoogleApiClient.reconnect();
            }
            //TODO fix else, find out request code
        } else {
            callbackManager.onActivityResult(requestCode, resultCode, data);
        }

    }

    @Override
    public void onConnected(Bundle bundle) {
        mSignInClicked = false;
        Toast.makeText(this, "User is connected!", Toast.LENGTH_LONG).show();
        getToken();
        if (Plus.PeopleApi.getCurrentPerson(mGoogleApiClient) != null) {
            Person currentPerson = Plus.PeopleApi.getCurrentPerson(mGoogleApiClient);
            String personName = currentPerson.getDisplayName();
            Person.Image personPhoto = currentPerson.getImage();

            profileName.setText(personName);
            Picasso.with(this).load(personPhoto.getUrl()).into(profileImage);
        }
    }

    private void getToken() {
        // execute AsyncTask
        new GetToken().execute(this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (!mIntentInProgress) {
            if (mSignInClicked && connectionResult.hasResolution()) {
                // The user has already clicked 'sign-in' so we attempt to resolve all
                // errors until the user is signed in, or they cancel.
                try {
                    connectionResult.startResolutionForResult(this, RC_SIGN_IN);
                    mIntentInProgress = true;
                } catch (IntentSender.SendIntentException e) {
                    // The intent was canceled before it was sent.  Return to the default
                    // state and attempt to connect to get an updated ConnectionResult.
                    mIntentInProgress = false;
                    mGoogleApiClient.connect();
                }
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.sign_in_google && !mGoogleApiClient.isConnecting()) {
            mSignInClicked = true;
            mGoogleApiClient.connect();
        } else if (v.getId() == R.id.button_sign_out) {
            // Sign out user if connected
            if (mGoogleApiClient.isConnected()) {
                Plus.AccountApi.clearDefaultAccount(mGoogleApiClient);
                mGoogleApiClient.disconnect();
            }
        }
    }

    /**
     * AsyncTask to get Google access token
     */
    public class GetToken extends AsyncTask<Context, Void, String> {

        @Override
        protected String doInBackground(Context... params) {
            String accessToken = null;
            try {
                accessToken = GoogleAuthUtil.getToken(
                        params[0],
                        Plus.AccountApi.getAccountName(mGoogleApiClient),
                        "oauth2:" + Scopes.PLUS_LOGIN
                );
            } catch (IOException transientEx) {
                // network or server error, the call is expected to succeed if you try again later.
                // Don't attempt to call again immediately - the request is likely to
                // fail, you'll hit quotas or back-off.
                transientEx.printStackTrace();
            } catch (UserRecoverableAuthException e) {
                // Recover
                accessToken = null;
            } catch (GoogleAuthException authEx) {
                // Failure. The call is not expected to ever succeed so it should not be
                // retried.
                authEx.printStackTrace();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return accessToken;
        }

        @Override
        protected void onPostExecute(String s) {
            Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
            super.onPostExecute(s);
        }
    }
}
