package com.example.nfc_study_03;


/*
# リファレンス
https://developer.android.com/develop/connectivity/nfc/nfc?hl=ja
https://developer.android.com/develop/connectivity/nfc/advanced-nfc?hl=ja
https://itnext.io/how-to-use-nfc-tags-with-android-studio-detect-read-and-write-nfcs-42f1d60b033?source=login--------------------------global_nav-----------&gi=85fff497b375
https://qiita.com/shimosyan/items/ed21fb6984240baa7397
https://www.nxp.com/docs/en/data-sheet/MF0ICU2.pdf
 */

import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    PendingIntent pendingIntent;

    String FLAG = "DEMO";
    String printerBtMAC = null;
    String printerWfMac = null;
    String printerSerial = null;
    String printerSku = null;

    //インテント フィルタを宣言
    IntentFilter iFilterNDEF = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
    IntentFilter iFilterTECH = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);

    IntentFilter[] intentFiltersArray = new IntentFilter[]{iFilterNDEF};

    // 処理するタグ テクノロジーの配列
    String[][] techListsArray = new String[][]{new String[]{Ndef.class.getName()}};

    NfcAdapter nfcAdapter;

    Tag tagFromIntent;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // NfcAdapterの宣言
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        // NFC機能が実装されていない機器に対する配慮
        if (nfcAdapter == null) {
            Toast.makeText(this, "NO NFC Capabilities",
                    Toast.LENGTH_SHORT).show();
            finish();
        }


        // Foregrund Dispatch
        pendingIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_MUTABLE);


        try {
            iFilterNDEF.addDataType("*/*");    /* Handles all MIME based dispatches.
                                       You should specify only the ones that you need. */
        } catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException("fail", e);
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        nfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        assert nfcAdapter != null;
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray);
        // nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

        // テスト用： 各種タグ情報の取得
        getTAGinformation(intent);

        /*
        // Payloadの読み取り(Page 4)
        String payload1Page = readTagPage(tagFromIntent, 4);
        Log.v(FLAG, "Payload-1page: " + payload1Page);

        // Payload の読み取り（NDEF MESG 範囲、4-39 Page for Mifare Ultralight)
        int startpage = 4;
        int endPage = 39;
        String payloadRange = readTagPageRange(tagFromIntent, startpage, endPage);
        Log.v(FLAG, "Payload-Range: " + payloadRange);

         */

        // Link-OS プリンタ情報の取得(ZQ620 でテスト)

        printerBtMAC = getPrinterInfo(19, 3, 15);
        Log.v(FLAG, "Printer BT-MAC: " + printerBtMAC);

        printerWfMac = getPrinterInfo(15, 3, 15);
        Log.v(FLAG, "Printer WF-MAC: " + printerWfMac);

        printerSerial = getPrinterInfo(28, 0, 14);
        Log.v(FLAG, "Printer Serial#: " + printerSerial);

        printerSku = getPrinterInfo(23, 2, 14);
        Log.v(FLAG, "Printer SKU: " + printerSku);

    }

    // Link-OS プリンタから特定ページデータの抽出
    public String getPrinterInfo(int targetPage, int startPos, int endPos) {
        String data = null;
        String getPage = readTagPage(tagFromIntent, targetPage);

        // テスト用
        //Log.v(FLAG, "Page-Data: " + getPage);

        if (getPage.length() != 16) {
            Log.v(FLAG, "Page-Length: Not 16 words. Please read the tag again.");
            return data = null;
        }

        data = getPage.substring(startPos, endPos);
        return data;
    }

    // 特定ページの抽出
    public String readTagPage(Tag tag, int page) {

        MifareUltralight mifare = MifareUltralight.get(tag);
        try {
            mifare.connect();
            byte[] payload = mifare.readPages(page);
            String payloadASCII = new String(payload, Charset.forName("US-ASCII"));

            // テスト用
            // Log.v(FLAG, "Page" + page + ": " + Arrays.toString(payload));
            // Log.v(FLAG, "Page" + page + ": " + payloadASCII);

            return payloadASCII;
        } catch (IOException e) {
            Log.e(FLAG, "IOException while reading MifareUltralight message...", e);
        } finally {
            if (mifare != null) {
                try {
                    mifare.close();
                } catch (IOException e) {
                    Log.e(FLAG, "Error closing tag...", e);
                }
            }
        }
        return null;
    }


    // 特定範囲のページを抽出
    public String readTagPageRange(Tag tag, int startpage, int endPage) {
        MifareUltralight mifare = MifareUltralight.get(tag);
        String payload = "";
        for (int i = startpage; i <= endPage; i++) {
            payload += readTagPage(tag, i);
        }
        return payload;
    }


    // NFCタグのメタデータ抽出
    private void getTAGinformation(Intent intent) {

        // インテントアクションを取得
        String action = intent.getAction();
        Log.v(FLAG, "Intent.GetAction: " + action);

        // タグで利用可能なTagTechnology を取得
        // https://developer.android.com/develop/connectivity/nfc/advanced-nfc?hl=ja#tag-tech
        Log.v(FLAG, "FLAG.getTechList.Length: " + tagFromIntent.getTechList().length);
        for (String i : tagFromIntent.getTechList()) {
            Log.v(FLAG, "FLAG.getTechList: " + i);
        }
    }


}