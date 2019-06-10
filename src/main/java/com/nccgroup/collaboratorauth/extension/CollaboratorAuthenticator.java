package com.nccgroup.collaboratorauth.extension;

import burp.IBurpExtender;
import burp.IBurpExtenderCallbacks;
import burp.IExtensionStateListener;
import com.coreyd97.BurpExtenderUtilities.DefaultGsonProvider;
import com.coreyd97.BurpExtenderUtilities.Preferences;
import com.nccgroup.collaboratorauth.extension.ui.ExtensionUI;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import static com.nccgroup.collaboratorauth.extension.Globals.*;

import javax.swing.*;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Security;

public class CollaboratorAuthenticator implements IBurpExtender, IExtensionStateListener {

    //Vars
    public static IBurpExtenderCallbacks callbacks;
    public static LogController logController;
    private ProxyService proxyService;
    private InteractionLogger interactionLogger;
    private Preferences preferences;

    //UI
    private ExtensionUI ui;

    public CollaboratorAuthenticator(){
        Security.addProvider(new BouncyCastleProvider());
        //Fix Darcula's issue with JSpinner UI.
        try {
            Class spinnerUI = Class.forName("com.bulenkov.darcula.ui.DarculaSpinnerUI");
            UIManager.put("com.bulenkov.darcula.ui.DarculaSpinnerUI", spinnerUI);
            Class sliderUI = Class.forName("com.bulenkov.darcula.ui.DarculaSliderUI");
            UIManager.put("com.bulenkov.darcula.ui.DarculaSliderUI", sliderUI);
        } catch (ClassNotFoundException e) {
            //Darcula is not installed.
        }
    }

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        CollaboratorAuthenticator.callbacks = callbacks;
        CollaboratorAuthenticator.logController = new LogController();

        //Setup preferences
        this.preferences = new Preferences(new DefaultGsonProvider(), callbacks);
        this.preferences.addSetting(PREF_COLLABORATOR_ADDRESS, String.class, "your.private.collaborator.instance");
        this.preferences.addSetting(PREF_POLLING_ADDRESS, String.class, "your.collaborator.authenticator.server");
        this.preferences.addSetting(PREF_POLLING_PORT, Integer.class, 5050);
        this.preferences.addSetting(PREF_REMOTE_SSL_ENABLED, Boolean.class, true);
        this.preferences.addSetting(PREF_IGNORE_CERTIFICATE_ERRORS, Boolean.class, false);
        this.preferences.addSetting(PREF_SSL_HOSTNAME_VERIFICATION, Boolean.class, true);
        this.preferences.addSetting(PREF_LOCAL_PORT, Integer.class, 32541);
        this.preferences.addSetting(PREF_SECRET, String.class, "Your Secret String");
        this.preferences.addSetting(PREF_ORIGINAL_COLLABORATOR_SETTINGS, String.class, "");
        this.preferences.addSetting(PREF_BLOCK_PUBLIC_COLLABORATOR, Boolean.class, true);

        this.interactionLogger = new InteractionLogger(this);

        SwingUtilities.invokeLater(() -> {
            this.ui = new ExtensionUI(this);
            CollaboratorAuthenticator.callbacks.addSuiteTab(this.ui);
            CollaboratorAuthenticator.callbacks.registerExtensionStateListener(this);
        });

        if((boolean) this.preferences.getSetting(PREF_BLOCK_PUBLIC_COLLABORATOR)){
            Utilities.blockPublicCollaborator();
        }
    }

    public void startCollaboratorProxy() throws IOException, URISyntaxException {
        boolean ssl = (boolean) this.preferences.getSetting(PREF_REMOTE_SSL_ENABLED);

        URI destination = new URI(ssl ? "https" : "http", null,
                (String) this.preferences.getSetting(PREF_POLLING_ADDRESS),
                (Integer) this.preferences.getSetting(PREF_POLLING_PORT), null, null, null);

        startCollaboratorProxy((Integer) this.preferences.getSetting(PREF_LOCAL_PORT), destination,
                (String) this.preferences.getSetting(PREF_SECRET));
    }

    public void startCollaboratorProxy(Integer listenPort, URI destinationURI, String secret) throws IOException {
        //Start the proxy service listening at the given location
        if(proxyService != null) proxyService.stop();

        boolean ignoreCertificateErrors = (boolean) this.preferences.getSetting(PREF_IGNORE_CERTIFICATE_ERRORS);
        boolean verifyHostname = (boolean) this.preferences.getSetting(PREF_SSL_HOSTNAME_VERIFICATION);
        proxyService = new ProxyService(listenPort, destinationURI, secret, ignoreCertificateErrors, verifyHostname);
        proxyService.start();

        saveCollaboratorConfig();
        callbacks.loadConfigFromJson(buildConfig(listenPort));
    }

    public void stopCollaboratorProxy(){
        if(proxyService != null) {
            proxyService.stop();
            proxyService = null;
            logController.logInfo("Polling Listener Stopped...");
        }
        restoreCollaboratorConfig();
    }

    public ProxyService getProxyService() {
        return proxyService;
    }

    private void saveCollaboratorConfig(){
        String config = callbacks.saveConfigAsJson(COLLABORATOR_SERVER_CONFIG_PATH);
        this.preferences.setSetting(PREF_ORIGINAL_COLLABORATOR_SETTINGS, config);
    }

    private void restoreCollaboratorConfig(){
        String config = (String) this.preferences.getSetting(PREF_ORIGINAL_COLLABORATOR_SETTINGS);
        callbacks.loadConfigFromJson(config);
    }

    private String buildConfig(int listenPort){
        return "{\"project_options\": {\"misc\": {\"collaborator_server\": " +
                "{\"location\": \"" + this.preferences.getSetting(PREF_COLLABORATOR_ADDRESS) + "\"," +
                "\"polling_location\": \"" + Inet4Address.getLoopbackAddress().getHostName() + ":" + listenPort + "\"," +
                "\"poll_over_unencrypted_http\": \"true\"," +
                "\"type\": \"private\"" +
                "}}}}";
    }

    @Override
    public void extensionUnloaded() {
        stopCollaboratorProxy();

        if((boolean) this.preferences.getSetting(PREF_BLOCK_PUBLIC_COLLABORATOR)){
            Utilities.unblockPublicCollaborator();
        }
    }

    public Preferences getPreferences() {
        return this.preferences;
    }

    public LogController getLogController() {
        return logController;
    }
}
