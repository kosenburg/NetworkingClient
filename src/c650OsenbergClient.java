/**
 * Created by Kevin on 11/22/2016.
 */
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class c650OsenbergClient {
    private static final String host = "localhost";
    private static final int serverPort = 21252;

    private Socket serverSocket;
    private DatagramSocket udpSocket;
    private int udpPortNum;

    private ByteArrayOutputStream fileData;
    private int fileLength;

    private TreeMap<Integer, byte[]> packetBuffer;

    private boolean notAllPacketsArrived = true;
    private InetAddress fileSenderaddress;
    private int fileSenderport;

    public c650OsenbergClient() throws UnknownHostException, IOException {
        setTcpIpSocket();
        setUdpSocket();
        setPacketBuffer();
    }

    private void setTcpIpSocket() throws IOException {
        serverSocket = new Socket(InetAddress.getByName(host), serverPort);
    }

    private void setUdpSocket() throws SocketException {
        udpPortNum = getUdpPortNumber();
        udpSocket = new DatagramSocket(udpPortNum);
    }

    private int getUdpPortNumber() {
        Random generator = new Random();
        return generator.nextInt(49151) + 16385;
    }

    private void setPacketBuffer() {
        packetBuffer = new TreeMap<>();
    }

    public void sendGreetingToServer() throws IOException {
        sendMessage();
        serverSocket.close();
    }

    private void sendMessage() throws IOException {
        String message = createMessage();
        BufferedWriter output = new BufferedWriter(new OutputStreamWriter(serverSocket.getOutputStream()));
        output.write(message);
        output.flush();
        output.close();
    }

    private String createMessage() {
        String message = "Hello," + String.valueOf(udpPortNum);
        return message;
    }

    public void receiveFileFromServer() throws IOException {
        listenOnUdpPort();

        sendAck();
        System.out.println("Ack sent " + fileSenderport);
        waitForTerminationMessage();

        combineDataFromPackets();
        writeDataToFile();
        checkFile();
        udpSocket.close();
        System.out.println("done " + fileSenderport);
    }

    public void listenOnUdpPort() throws IOException {
        while (notAllPacketsArrived) {
            DatagramPacket incomingPacket = new DatagramPacket(new byte[100], 100);
            udpSocket.receive(incomingPacket);

            fileSenderaddress = incomingPacket.getAddress();
            fileSenderport = incomingPacket.getPort();

            byte[] temp = incomingPacket.getData();
            int packetNumber = (int) temp[0];

            storePacketInfo(packetNumber, temp);
        }
    }

    private void storePacketInfo(int packetNumber, byte[] data) {
        if (!packetBuffer.containsKey(packetNumber)) {
            System.out.println("Packet " + packetNumber + " received!");
            data = cleanupPacket(data);

            fileLength = data[1];
            packetBuffer.put(packetNumber, Arrays.copyOfRange(data, 2, data.length));

            checkAllPacketsArrived();
        }
    }

    private byte[] cleanupPacket(byte[] lastPacketData) {
        ByteArrayOutputStream container = new ByteArrayOutputStream();
        for (byte data : lastPacketData) {
            if (data != 0) {
                container.write(data);
            }
        }
        return container.toByteArray();
    }

    private void checkAllPacketsArrived() {
        ByteArrayOutputStream container = new ByteArrayOutputStream();
        for (int key: packetBuffer.keySet()) {
            byte[] packetInfo = packetBuffer.get(key);
            container.write(packetInfo, 0, packetInfo.length);
        }

        if (container.toByteArray().length == fileLength) {
            notAllPacketsArrived = false;
        }
    }

    private void sendAck() throws IOException {
        String ack = "ack";
        DatagramPacket ackMessage = new DatagramPacket(ack.getBytes(), ack.getBytes().length, fileSenderaddress, fileSenderport);
        udpSocket.send(ackMessage);
    }

    private void waitForTerminationMessage() throws IOException {
        DatagramPacket okayMessage = new DatagramPacket(new byte[2], 2);
        udpSocket.receive(okayMessage);
        String message = new String (okayMessage.getData());

        while(!message.equals("ok")) {
            sendAck();
            System.out.println("Ack resent " + udpPortNum);
            udpSocket.receive(okayMessage);
            message = new String(okayMessage.getData());
        }
    }

    private void combineDataFromPackets() {
        fileData = new ByteArrayOutputStream();
        for (int i = 1; i <= packetBuffer.lastKey(); i++) {
            byte[] packetInfo = packetBuffer.get(i);
            fileData.write(packetInfo, 0, packetInfo.length);
        }
    }

    private void writeDataToFile() throws IOException {
        try (FileOutputStream fos = new FileOutputStream(new File("copy-of-file.txt"))) {
            fileData.writeTo(fos);
            fileData.flush();
            fileData.close();
        }
    }

    private void checkFile() {
        File file = new File("copy-of-file.txt");
        if (file.length() == fileLength)
            System.out.println("Success");
        else
            System.out.println("Not Success");
    }

    public static void main(String[] args) {
        try {
            c650OsenbergClient client = new c650OsenbergClient();
            client.sendGreetingToServer();
            client.receiveFileFromServer();
        } catch (IOException e) {
            System.err.println("Issue due to: " + e.getMessage());
        }
    }
}


