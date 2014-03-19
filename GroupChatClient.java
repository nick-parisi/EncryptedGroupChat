package edu.bc.cs.parisin.cs344.groupchatclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;


import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

public class GroupChatClient extends Activity {
	private TextView output, pass;
	private ScrollView scroll;
	private static final int KEY_LENGTH = 128;
	private String connected;
	private PrintWriter out;
	private boolean connectThreadDone;
	private Thread currentConnection;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_group_chat_client);
		output = (TextView)findViewById(R.id.chatTextField);
		scroll = (ScrollView)output.getParent();
		pass = (TextView)findViewById(R.id.passwordtext);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.group_chat_client, menu);
		return true;
	}
	
	public void send(View view) {
		connectThreadDone = false;
		ConnectivityManager connMgr = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
		if (networkInfo != null && networkInfo.isConnected()) {
			//connected, grab name of group we are trying to connect to and group we are connected to (if it exists)
			EditText et = (EditText)findViewById(R.id.grouptext);
			String group = et.getText().toString();
			String conn = connected;
			//don't connect again if just trying to send message to connected group
			if (!group.equals(conn)) {
				if (currentConnection != null) { currentConnection.interrupt(); } //interrupt old thread, since we don't want to receive messages from old group
				currentConnection = new Thread(new Runnable() {
					public void run() { connect(); }
				});
				currentConnection.start();
			}
			else {
				connectThreadDone = true;
			}
			//now let's transmit our message
			//wait for other thread first
			while (connectThreadDone == false) {} //normally we would use currentConnection.join() to wait, but that thread will run continuously
			//okay, read to transmit
			new Thread(new Runnable() {
				public void run() {
					transmit();
				}
			}).start();
		}
		else {
			output.setText(getString(R.string.notConnected));
		}
	}
	
	public void transmit() {
		final EditText message = (EditText)findViewById(R.id.messagetext);
		String clearText = message.getText().toString();
		if (clearText.equals("")) { return; } //don't transmit if there's nothing to send!
		message.post(new Runnable() {
			public void run() { message.setText(""); } //clear 
		});
		String encryptedText = encrypt(clearText);
		encryptedText = encryptedText.replaceAll("\\n", ""); //encrypt appends an extra newline character that is undesired
		out.println(encryptedText);
	}
	
	public void connect() {
		Socket socket = null;
		String source = "cslab.bc.edu";
		BufferedReader in = null;
		out = null;
		EditText et = (EditText)findViewById(R.id.grouptext);
		String group = et.getText().toString();
		
		try {
			socket = new Socket(source, 10000);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(socket.getOutputStream(), true);
			//now connected, let's remember this so we don't try and connect every time we send a message.
			connected = group;
			
			//step one, send group name with new line character
			out.println(group);
			
			//done connecting
			connectThreadDone = true;
			
			//wait for more messages from server
			while (Thread.currentThread().isInterrupted() == false) {
				String nextLine;
				if ((nextLine = in.readLine()) != null) {
					final String decrypted = decrypt(nextLine);
					output.post(new Runnable() { 
						public void run() {
							output.append(decrypted + "\n");
							scroll.fullScroll(View.FOCUS_DOWN);
						}
					});
				}
			}
		}
		catch (IOException e) { output.setText("oops"); }
		catch (Exception e) { output.setText("oops"); }
		finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
					//tried!
				}
			}
		}
				
	}
	
	// make key be KEY_LENGTH bits
	//Taken from Professor Ames Demo06-Secret Codes
	private String fixKey(String key) {
		if (key.length() > KEY_LENGTH/8)
			key = key.substring(0, KEY_LENGTH/8);
		while (key.length() < KEY_LENGTH/8)
			key += " ";
		return key;
	}
	
	//Mostly taken from Professor Ames Demo06-Secret Codes
	public String decrypt(String message) {
		String cipherTextString = message;
		byte[] cipherText = Base64.decode(cipherTextString, Base64.DEFAULT);
		byte[] result = encryptDecryptHelper(cipherText, Cipher.DECRYPT_MODE);
		String decrypted = new String(result);
		if (decrypted.trim().length() == 0) { //when decryption fails, a string of only white space will be returned. In that case, just display original encrypted text
			return cipherTextString;
		}
		else { return decrypted; }
	}
	
	//Mostly taken from Professor Ames Demo06-Secret Codes
	public String encrypt(String message) {
		byte[] result = encryptDecryptHelper(message.getBytes(), Cipher.ENCRYPT_MODE);
		String finalStr = Base64.encodeToString(result, Base64.DEFAULT);
		return finalStr;
	}
	
	//Mostly taken from Professor Ames Demo06-Secret Codes
	public byte[] encryptDecryptHelper(byte[] oldBytes, int mode) {
		String key = ((EditText)findViewById(R.id.passwordtext)).getText().toString();
		key = fixKey(key);
		Cipher cipher;
		try {
			cipher = Cipher.getInstance("AES");
			SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
			cipher.init(mode, keySpec);
			byte[] newBytes = cipher.doFinal(oldBytes);
			return newBytes; // normal case, no problems
		} catch (InvalidKeyException e) {
			return new byte[] {0};
		} catch (NoSuchAlgorithmException e) {
			// Handled below
		} catch (NoSuchPaddingException e) {
			// Handled below
		} catch (IllegalBlockSizeException e) {
			// Handled below
		} catch (BadPaddingException e) {
			// Handled below
		}
		// If we didn't return in the try block, something went wrong
		return new byte[] {0};
	}
	
	
}


