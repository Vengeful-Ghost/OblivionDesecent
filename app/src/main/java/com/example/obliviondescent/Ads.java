package com.example.obliviondescent;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

/**
 * Opt-in rewarded ads (AdMob): the player chooses to watch one for a revive or double Souls.
 * Ads only load once the consent check (UMP, from AdMob's Privacy & messaging) allows them.
 * The ad unit comes from build.gradle.kts: test ads unless built with -PliveAds.
 */
final class Ads {

    private static RewardedAd ready;
    private static boolean loading, started;

    private Ads() {}

    /**
     * Refreshes the player's consent on every launch and shows the consent form where the law needs one
     * (EEA, UK, some US states); ads start once consent allows them.
     */
    static void gatherConsent(Activity activity) {
        ConsentInformation consent = UserMessagingPlatform.getConsentInformation(activity);
        consent.requestConsentInfoUpdate(activity, new ConsentRequestParameters.Builder().build(),
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, error -> preload(activity)),
                error -> preload(activity));
        preload(activity); // consent from an earlier session may already allow ads
    }

    /** True where players must be able to change their ad choices later (Settings shows a button). */
    static boolean privacyOptionsRequired(Context context) {
        return UserMessagingPlatform.getConsentInformation(context).getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    static void showPrivacyOptions(Activity activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, error -> preload(activity));
    }

    /** Starts the SDK once and keeps one ad loaded so it can show without waiting. */
    static void preload(Context context) {
        Context app = context.getApplicationContext();
        if (!UserMessagingPlatform.getConsentInformation(app).canRequestAds()) return;
        if (!started) {
            started = true;
            MobileAds.initialize(app, status -> {});
        }
        if (ready != null || loading) return;
        loading = true;
        RewardedAd.load(app, BuildConfig.REWARDED_AD_UNIT, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                ready = ad;
                loading = false;
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                loading = false;
            }
        });
    }

    static boolean isReady() {
        return ready != null;
    }

    /** Shows the loaded ad; onReward runs only if it was watched through, onClosed always runs. */
    static void show(Activity activity, Runnable onReward, Runnable onClosed) {
        RewardedAd ad = ready;
        ready = null;
        if (ad == null) {
            onClosed.run();
            return;
        }
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                onClosed.run();
                preload(activity);
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                onClosed.run();
                preload(activity);
            }
        });
        ad.show(activity, reward -> onReward.run());
    }
}
