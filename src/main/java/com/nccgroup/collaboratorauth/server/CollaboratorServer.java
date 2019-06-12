package com.nccgroup.collaboratorauth.server;

import nu.studer.java.util.OrderedProperties;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.ssl.SSLContexts;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.SecretKeyFactory;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.security.*;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class CollaboratorServer {

    private static final String DEFAULT_KEYSTORE_PASSWORD = "changeit";
    private static final String COLLABORATOR_SERVER_ADDRESS = "collaborator_server_address";
    private static final String COLLABORATOR_SERVER_PORT = "collaborator_server_port";
    private static final String COLLABORATOR_SERVER_ISHTTPS = "collaborator_server_ishttps";
    private static final String SECRET = "secret";
    private static final String LISTEN_PORT = "listen_port";
    private static final String LISTEN_ADDRESS = "listen_address";
    private static final String ENABLE_SSL = "enable_ssl";
    private static final String PRIVATE_KEY_PATH = "ssl_private_key_path";
    private static final String CERTIFICATE_PATH = "ssl_certificate_path";
    private static final String INTERMEDIATE_CERTIFICATE_PATH = "ssl_intermediate_certificate_path";
    private static final String INTERMEDIATE_CERTIFICATE_DEFAULT = "/certs/intermediate.crt";
    private static final String KEYSTORE_FILE = "keystore_file";
    private static final String KEYSTORE_PASSWORD = "keystore_password";
    private static final String KEYSTORE_KEY_PASSWORD = "keystore_key_password";
    private static final String LOG_LEVEL = "log_level";

    private HttpServer server;
    private Integer listenPort;
    private String logLevel;

    private CollaboratorServer(Properties properties) throws Exception {
        String actualAddress = properties.getProperty(COLLABORATOR_SERVER_ADDRESS);
        Integer actualPort = Integer.parseInt(properties.getProperty(COLLABORATOR_SERVER_PORT));
        boolean actualIsHttps = Boolean.parseBoolean(properties.getProperty(COLLABORATOR_SERVER_ISHTTPS));

        listenPort = Integer.parseInt(properties.getProperty(LISTEN_PORT));
        InetAddress listenAddress = InetAddress.getByName(properties.getProperty(LISTEN_ADDRESS));
        boolean enableSSL = Boolean.parseBoolean(properties.getProperty(ENABLE_SSL));
        logLevel = properties.getProperty(LOG_LEVEL);

        String secret = properties.getProperty(SECRET).trim();

        ServerBootstrap serverBootstrap = ServerBootstrap.bootstrap()
                .setConnectionReuseStrategy(new NoConnectionReuseStrategy())
                .setListenerPort(listenPort)
                .setLocalAddress(listenAddress)
                .registerHandler("*", new HttpHandler(actualAddress, actualPort, actualIsHttps, secret, logLevel));

        if(enableSSL){
            System.out.println("Starting server in HTTPS mode. Creating SSL context.");
            SSLContext sslContext;
            if(!properties.getProperty(PRIVATE_KEY_PATH).equals("")){
                //Load private key
                System.out.println("Loading private key from file: " + properties.getProperty(PRIVATE_KEY_PATH));
                PrivateKey privateKey = Utilities.loadPrivateKeyFromFile(properties.getProperty(PRIVATE_KEY_PATH));

                ArrayList<Certificate> certificateList = new ArrayList<>();
                //Load certificate
                System.out.println("Loading certificate from file: " + properties.getProperty(CERTIFICATE_PATH));
                certificateList.add(Utilities.loadCertificateFromFile(properties.getProperty(CERTIFICATE_PATH)));
                //Load intermediate certificate
                String intermediatePath = properties.getProperty(INTERMEDIATE_CERTIFICATE_PATH);
                if(!intermediatePath.equals("") && !intermediatePath.equals(INTERMEDIATE_CERTIFICATE_DEFAULT)){
                    System.out.println("Loading intermediate certificate from file: " + properties.getProperty(INTERMEDIATE_CERTIFICATE_PATH));
                    certificateList.add(Utilities.loadCertificateFromFile(intermediatePath));
                }
                Certificate[] certificateChain = certificateList.toArray(new Certificate[0]);

                //Create new keystore
                KeyStore keyStore = KeyStore.getInstance("JKS");
                keyStore.load(null, DEFAULT_KEYSTORE_PASSWORD.toCharArray());
                keyStore.setKeyEntry("collaboratorAuth", privateKey,
                        DEFAULT_KEYSTORE_PASSWORD.toCharArray(), certificateChain);
//                keyStore.
                sslContext = createSSLContext(keyStore, DEFAULT_KEYSTORE_PASSWORD.toCharArray());
            }else {
                File keystoreFile = new File(properties.getProperty(KEYSTORE_FILE));
                String storePassword = properties.getProperty(KEYSTORE_PASSWORD);
                String keyPassword = properties.getProperty(KEYSTORE_KEY_PASSWORD);
                sslContext = createSSLContext(keystoreFile, storePassword, keyPassword);
            }
            serverBootstrap.setSslContext(sslContext);
        }else{
            System.out.println("Starting server in HTTP mode.");
        }

        if(logLevel.equalsIgnoreCase("debug") || logLevel.equalsIgnoreCase("error")) {
            System.out.println("Shared Secret: " + secret);
            serverBootstrap.setExceptionLogger(ex -> {
                System.out.println(ex.getMessage());
                ex.printStackTrace();
            });
        }

        server = serverBootstrap.create();
    }

    public void start() throws IOException {
        if(server != null) {
            server.start();
            System.out.println("Server started. Listening for poll requests on port " + listenPort + "...");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.shutdown(500, TimeUnit.MILLISECONDS);
            }));
        }
    }

    private SSLContext createSSLContext(final File keyStoreFile, final String storePassword, final String keyPassword) throws Exception {
        return SSLContexts.custom()
                .loadKeyMaterial(keyStoreFile, storePassword.toCharArray(), keyPassword.toCharArray())
                .build();
    }

    private SSLContext createSSLContext(final KeyStore keyStore, final char[] password) throws Exception {
        return SSLContexts.custom().loadKeyMaterial(keyStore, password).build();
    }

    public static void main(String[] args) throws IOException {

        checkBouncyCastle();

        OrderedProperties properties = getDefaultProperties();
        if(args.length == 0){
            //Create default properties file
            File defaultsFile = new File("CollaboratorServer.properties");
            if(defaultsFile.exists()){
                System.err.println("Could not create the defaults file. File exists.");
                System.err.println("Start the server with `java -jar CollaboratorAuth.jar " + defaultsFile.getName() + "`" +
                        " or remove the file to allow it to be populated with the defaults");
                return;
            }
            FileOutputStream outputStream = new FileOutputStream(defaultsFile);
            properties.store(outputStream, "MAKE SURE THE SECRET IS CHANGED TO SOMETHING MORE SECURE!\n" +
                    "By default, the private key and certificates will be used to\n" +
                    "configure the SSL context. To use a keystore instead, comment out " + PRIVATE_KEY_PATH + ".");
            System.out.println("Default config written to " + defaultsFile.getName());
            System.out.println("Edit the config (especially the secret!)");
            System.out.println("Then start the server with `java -jar CollaboratorAuth.jar " + defaultsFile.getName() + "`");
            return;
        }else{
            File configFile = new File(args[0]);
            if(!configFile.exists()){
                System.err.println("Config file does not exist. Run the jar without arguments to generate the default config.");
                return;
            }else{
                FileInputStream inputStream = new FileInputStream(configFile);
                try {
                    properties.load(inputStream);
                }finally {
                    inputStream.close();
                }
            }
        }

        try {
            CollaboratorServer server = new CollaboratorServer(properties.toJdkProperties());
            server.start();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private static void checkBouncyCastle(){
        if(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null){
            int result = Security.addProvider(new BouncyCastleProvider());
            if(result == -1){
                System.err.println("Could not load Bouncy Castle Provider! Exiting...");
                System.exit(0);
            }

            try{
                SecretKeyFactory.getInstance("PBEWITHSHA256AND128BITAES-CBC-BC", "BC");
            } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
                System.err.println("Could not instantiate the SecretKeyFactory, Bouncy Castle must not have loaded!");
                e.printStackTrace();
                System.exit(0);
            }
        }
    }

    private static OrderedProperties getDefaultProperties(){
        OrderedProperties defaultProperties = new OrderedProperties.OrderedPropertiesBuilder()
                .withSuppressDateInComment(true).build();
        defaultProperties.setProperty(COLLABORATOR_SERVER_ADDRESS, "127.0.0.1");
        defaultProperties.setProperty(COLLABORATOR_SERVER_PORT, "80");
        defaultProperties.setProperty(LISTEN_PORT, "5050");
        defaultProperties.setProperty(LISTEN_ADDRESS, "0.0.0.0");
        defaultProperties.setProperty(ENABLE_SSL, "false");
        defaultProperties.setProperty(PRIVATE_KEY_PATH, "/certs/key.pem.pkcs8");
        defaultProperties.setProperty(CERTIFICATE_PATH, "/certs/cert.crt");
        defaultProperties.setProperty(INTERMEDIATE_CERTIFICATE_PATH, INTERMEDIATE_CERTIFICATE_DEFAULT);
        defaultProperties.setProperty(KEYSTORE_FILE, "/path/to/java/keystore");
        defaultProperties.setProperty(KEYSTORE_PASSWORD, "NEW_PASSWORD_FOR_KEYSTORE");
        defaultProperties.setProperty(KEYSTORE_KEY_PASSWORD, "NEW_PASSWORD_FOR_PRIVATE_KEY");

        defaultProperties.setProperty(SECRET, "CHANGE_ME");
        defaultProperties.setProperty(LOG_LEVEL, "INFO");

        return defaultProperties;
    }
}
