package fi.bitrite.android.ws.auth.http;

import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.util.Log;

import fi.bitrite.android.ws.api.RestClient;
import fi.bitrite.android.ws.auth.AuthenticationHelper;
import fi.bitrite.android.ws.auth.NoAccountException;
import fi.bitrite.android.ws.util.GlobalInfo;
import fi.bitrite.android.ws.util.http.HttpException;

import org.apache.http.NameValuePair;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for authenticating the user against the WarmShowers web service.
 */
public class HttpAuthenticator {

    private final String wsUserAuthUrl = GlobalInfo.warmshowersBaseUrl + "/services/rest/user/login";
    private final String wsUserLogoutUrl = GlobalInfo.warmshowersBaseUrl + "/services/rest/user/logout";

    private static final String TAG = "HttpAuthenticator";

    private List<NameValuePair> getCredentialsFromAccount() throws OperationCanceledException, AuthenticatorException, IOException, NoAccountException {
        List<NameValuePair> credentials = new ArrayList<NameValuePair>();

        String username = AuthenticationHelper.getAccountUsername();
        String password = AuthenticationHelper.getAccountPassword();

        credentials.add(new BasicNameValuePair("username", username));
        credentials.add(new BasicNameValuePair("password", password));
        return credentials;
    }

    /**
     * Hits the logout service and then the login.
     *
     * Returns
     * - userid
     * - 0 if already logged in
     */
    public int authenticate() throws HttpAuthenticationFailedException, IOException, JSONException, NoAccountException {
        RestClient authClient = new RestClient();
        int userId = 0;

        try {
            // Log.i(TAG, "Routine logout attempt");
            authClient.authpost(wsUserLogoutUrl);
        } catch (Exception e) {
            Log.i(TAG, "Exception on logout - not to worry: " + e.toString());
            // We don't care a lot about this, as we were just trying to ensure clean login.
        }


        try {
            List<NameValuePair> credentials = getCredentialsFromAccount();

            // Log.i(TAG, "Normal login attempt after logout credentials=" + credentials.toString());
            JSONObject authResult = authClient.authpost(wsUserAuthUrl, credentials);

            userId = authResult.getJSONObject("user").getInt("uid");
            String cookieSessionName = authResult.getString("session_name");
            String cookieSessionId = authResult.getString("sessid");

            AuthenticationHelper.addCookieInfo(cookieSessionName, cookieSessionId, userId);

        } catch (ClientProtocolException e) {
            if (e.getCause() instanceof CircularRedirectException) {
                // If we get this authentication has still been successful, so ignore it
            } else {
                throw new HttpAuthenticationFailedException(e);
            }
        } catch (IOException e) {
            // Rethrow, prevent the catch below from getting to it. we want to know this was IO exception
            throw e;
        } catch (HttpAuthenticationFailedException e) {  // Attempting to do auth with wrong credentials
            throw e;
        } catch (NoAccountException e) {
            throw e;
        } catch (HttpException e) {
            if (e.getMessage().equals("406")) {
                Log.i(TAG, "Got error 406 attempting to log in, so ignoring");
                // This is the case where we hit auth and were already authenticated... but shouldn't have happened.
            } else {
                throw e;
            }
        } catch (Exception e) {
            // We might have had a json parsing or access exception - for example, if the "user" was not there,
            // Could also have AuthenticatorException or OperationCancelledException here
            // or if there was something wrong with what the server returned
            throw new HttpAuthenticationFailedException(e);
        }

        return userId;
    }

}