package src.server;

import src.others.FileHelper;
import src.others.Utils;
import src.client.MessageCode;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

public class ServerThread extends Thread {
    private static final String IMAGE_DIR_PATH = "./output/server/img/";

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private ServerManager manager;
    private String userID;
    private String deviceID;
    private boolean isRunning;

    public ServerThread(Socket socket, String keystorePath, String keystorePwd,
            String apiKey) {
        this.socket = socket;
        this.userID = null;
        this.deviceID = null;
        this.isRunning = true;
    }

    public void run() {
        System.out.println("Accepted connection!");

        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            manager = ServerManager.getInstance();

            while (isRunning) {
                MessageCode opcode = (MessageCode) in.readObject();
                switch (opcode) {
                    case AU:
                        authUser();
                        break;
                    case AD:
                       authDevice();
                        //---------------------------------------
                        break;


                    case TD:
                        attestClient();
                        break;
                    case CREATE:
                        createDomain();
                        break;
                    case ADD:
                        addUserToDomain();
                        break;
                    case RD:
                        registerDeviceInDomain();
                        break;
                    case ET:
                        registerTemperature();
                        break;
                    case EI:
                        registerImage();
                        break;
                    case RT:
                        getTemperatures();
                        break;
                    case RI:
                        getImage();
                        break;
                    case STOP:
                        stopThread();
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (CertificateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SignatureException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void stopThread() {
        System.out.println("Client quits. killing thread");
        manager.disconnectDevice(this.userID, this.deviceID);
        isRunning = false;
    }

    private void authUser() throws ClassNotFoundException, IOException,
            InvalidKeyException, CertificateException, NoSuchAlgorithmException,
            SignatureException {
        System.out.println("Starting user auth.");
        ServerAuth sa = IoTServer.SERVER_AUTH;
        userID = (String) in.readObject();

        long nonce = sa.generateNonce();
        out.writeLong(nonce);

        if (sa.isUserRegistered(userID)) {
            out.writeObject(MessageCode.OK_USER);
            authRegisteredUser(nonce); //certificado
        } else {
            out.writeObject(MessageCode.OK_NEW_USER);
            authUnregisteredUser(nonce); //certificado
        }





        int twoFACode = sa.generate2FACode();
        int emailResponseCode = sa.send2FAEmail(userID, twoFACode);
        // Handle bad email response code
        while (emailResponseCode != 200) {
            twoFACode = sa.generate2FACode();
            emailResponseCode = sa.send2FAEmail(userID, twoFACode);
        }


        int receivedTwoFACode = in.readInt();

        if (twoFACode == receivedTwoFACode) {
            out.writeObject(MessageCode.OK);
        } else {
            out.writeObject(MessageCode.NOK);
        }
    }

    private void authDevice() throws IOException, ClassNotFoundException {
        String deviceID = (String) in.readObject();
        MessageCode res = manager.authenticateDevice(userID, deviceID).responseCode();
        if (res == MessageCode.OK_DEVID) {
            this.deviceID = deviceID;
        }
        out.writeObject(res);
    }

    private void attestClient() throws ClassNotFoundException, IOException,
            NoSuchAlgorithmException {
        long nonce = ServerAuth.generateNonce();
        out.writeLong(nonce);
        out.flush();

        byte[] receivedHash = (byte[]) in.readObject();
        if (ServerAuth.verifyAttestationHash(receivedHash, nonce)) {
            out.writeObject(MessageCode.OK_TESTED);
        } else {
            manager.disconnectDevice(userID, deviceID);
            out.writeObject(MessageCode.NOK_TESTED);
        }
    }

    private void createDomain() throws IOException, ClassNotFoundException {
        String domain = (String) in.readObject();
        MessageCode res = manager.createDomain(userID, domain).responseCode();
        out.writeObject(res);
    }

    private void addUserToDomain() throws IOException, ClassNotFoundException {
        String newUser = (String) in.readObject();
        String domain = (String) in.readObject();
        MessageCode res = manager.addUserToDomain(userID, newUser, domain).responseCode();
        out.writeObject(res);
    }

    private void registerDeviceInDomain() throws IOException, ClassNotFoundException {
        String domain = (String) in.readObject();
        MessageCode res = manager.registerDeviceInDomain(domain, this.userID, this.deviceID).responseCode();
        out.writeObject(res);
    }

    private void registerTemperature() throws IOException, ClassNotFoundException {
        String tempStr = (String) in.readObject();
        float temperature;
        try {
            temperature = Float.parseFloat(tempStr);
        } catch (NumberFormatException e) {
            out.writeObject(new ServerResponse(MessageCode.NOK));
            out.flush();
            return;
        }

        MessageCode res = manager
                .registerTemperature(temperature, this.userID, this.deviceID)
                .responseCode();
        out.writeObject(res);
        out.flush();
    }

    private void registerImage() throws IOException, ClassNotFoundException {
        String filename = (String) in.readObject();
        long fileSize = (long) in.readObject();
        String fullImgPath = IMAGE_DIR_PATH + filename;

        FileHelper.receiveFile(fileSize, fullImgPath, in);

        MessageCode res = manager
                .registerImage(filename, this.userID, this.deviceID)
                .responseCode();
        out.writeObject(res);
    }

    private void getTemperatures() throws IOException, ClassNotFoundException {
        String domain = (String) in.readObject();
        ServerResponse sResponse = manager.getTemperatures(this.userID, domain);
        MessageCode res = sResponse.responseCode();
        out.writeObject(res);
        if (res == MessageCode.OK) {
            // FileHelper.sendFile(sResponse.filePath(),out);
            out.writeObject(sResponse.temperatures());
        }
    }

    private void getImage() throws IOException, ClassNotFoundException {
        String targetUser = (String) in.readObject();
        String targetDev = (String) in.readObject();
        ServerResponse sr = manager.getImage(this.userID, targetUser, targetDev);
        MessageCode rCode = sr.responseCode();
        // Send code to client
        out.writeObject(rCode);
        // Send file (if aplicable)
        if (rCode == MessageCode.OK) {
            FileHelper.sendFile(sr.filePath(), out);
        }
    }

    private void authUnregisteredUser(long nonce) throws IOException,
            ClassNotFoundException, InvalidKeyException, CertificateException,
            NoSuchAlgorithmException, SignatureException {
        ServerAuth sa = IoTServer.SERVER_AUTH;

        long receivedUnsignedNonce = in.readLong();
        byte[] signedNonce = (byte[]) in.readObject();
        Certificate cert = (Certificate) in.readObject();

        if (sa.verifySignedNonce(signedNonce, cert, nonce) &&
                receivedUnsignedNonce == nonce) {
            sa.registerUser(userID, Utils.certPathFromUser(userID));
            sa.saveCertificateInFile(userID, cert);
            out.writeObject(MessageCode.OK);
        } else {
            out.writeObject(MessageCode.WRONG_NONCE);
        }
    }

    private void authRegisteredUser(long nonce) throws ClassNotFoundException,
            IOException, InvalidKeyException, CertificateException,
            NoSuchAlgorithmException, SignatureException {
        ServerAuth sa = IoTServer.SERVER_AUTH;

        byte[] signedNonce = (byte[]) in.readObject();
        if (sa.verifySignedNonce(signedNonce, userID, nonce)) {
            out.writeObject(MessageCode.OK);
        } else {
            out.writeObject(MessageCode.WRONG_NONCE);
        }
    }
}