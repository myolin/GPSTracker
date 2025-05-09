package com.myolin.followme.Utils;

import static androidx.core.content.ContextCompat.getSystemService;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

public class NetworkChecker {

    public static boolean hasNetworkConnection(Context context) {
        ConnectivityManager connectivityManager = getSystemService(context, ConnectivityManager.class);
        if (connectivityManager == null) {
            return false;
        }
        Network network = connectivityManager.getActiveNetwork();
        NetworkCapabilities nc = connectivityManager.getNetworkCapabilities(network);
        if (nc != null) {
            return nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
        }
        return false;
    }
}
