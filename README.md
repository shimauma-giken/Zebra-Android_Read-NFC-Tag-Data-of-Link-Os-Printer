### Link-OS プリンタのNFCタグデータをZebra Android 端末にて抽出するJava コード（Android)

-----

下記の用途でZebra Android 端末を用いて、Zebra Link-OSプリンタのNFC/NDEFメッセージを抽出する必要がある際に参考になさってください。

- Blueooth タッチペアリングのため、Bluetooth MACを抽出
- Wi-Fi タッチペアリングのため、WiFi MACを抽出
- 運用・保守のため、SKUやシリアルを抽出
<br>

応用すると、RFD40 やスキャナへも流用が可能です。  
<br>


```java
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
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    PendingIntent pendingIntent;

    String FLAG = "DEMO";

    // Printer情報格納用
    // {Wireless=000000000000, Bluetooth=5cf8218dc99b, Serial=50J163001771, Ether=00074d6c9e65, SKU=ZD41H23-D0PE00EZ}
    Map<String, String> prtInfo = new HashMap<>();


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


        // Foreground Dispatch
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

        // Payloadの読み取り(Page 4)
        String payload1Page;
        payload1Page = readTagPage(tagFromIntent, 4);
        payload1Page += readTagPage(tagFromIntent, 8);
        payload1Page += readTagPage(tagFromIntent, 12);
        Log.v(FLAG, "Payload-1page: " + payload1Page);

        // Mifare UltralightのNDEF の読み取り
        // MESG 範囲は 4-39 Page
        int startPage = 4;
        int endPage = 39;
        String payloadRange = readTagPageRange(tagFromIntent, startPage, endPage);
        Log.v(FLAG, "Payload-Range: " + payloadRange);


        // Link-OS Printer 情報の収集
        prtInfo = getPrinterInfo(payloadRange);
        // テスト用
        Log.v("DEM0", prtInfo.toString());
    }


    // Link-OS プリンタから特定ページデータの抽出
    // Link-OS Printer NDEF Message 例、
    //　p�lUzebra.com/apps/r/nfc?mE=000000000000&mW=ac3fa449af4c&mB=ac3fa449af4d&c=ZQ51-AUN010A-00&s=XXRAJ151700742&v=0�
    //  p�lUzebra.com/apps/r/nfc?mE=00074d6c9e65&mW=000000000000&mBL=5cf8218dc99b&c=ZD41H23-D0PE00EZ&s=50J163001771&v=0�
    //
    //    データ構造
    //    p�lUzebra.com/apps/r/nfc
    //    1. ?mE=000000000000      Ethernet Mac
    //    2. &mW=ac3fa449af4c      Wi-Fi Mac
    //    3. &mB=ac3fa449af4d      Bluetooth Mac（Classic）
    //    3. &mBL=5cf8218dc99b     Bluetooth Mac（LE）
    //    4. &c=ZQ51-AUN010A-00    SKU
    //    5. &s=XXRAJ151700742     Serial #
    //    6. &v=0�                 Version

    public Map<String, String> getPrinterInfo(String payload) {
        Map<String, String> prtInfo = new HashMap<>();

        // Blutooth Classic/ Low Energy 対策用
        int offset = 4;

        // キーワードのポジション取得
        int posEther = payload.indexOf("?mE=");
        int posWireless = payload.indexOf("&mW=");
        int posBluetooth;
        if (payload.indexOf("&mB=") > 0){
            posBluetooth = payload.indexOf("&mB=");
        } else {
            posBluetooth = payload.indexOf("&mBL=");
            offset = 5;
        }
        int posSKU = payload.indexOf("&c=");
        int posSerial = payload.indexOf("&s=");
        int posVer = payload.indexOf("&v=");

        // PrtInfo Mapにデータ格納
        prtInfo.put("Ether",payload.substring(posEther+4, posWireless));
        prtInfo.put("Wireless",payload.substring(posWireless+4, posBluetooth));
        prtInfo.put("Bluetooth",payload.substring(posBluetooth+offset, posSKU));
        prtInfo.put("SKU",payload.substring(posSKU+3, posSerial));
        prtInfo.put("Serial",payload.substring(posSerial+3, posVer));

        return prtInfo;
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


    // 特定範囲のページを抽出 v2
    public String readTagPageRange(Tag tag, int startPage, int endPage) {
        MifareUltralight mifare = MifareUltralight.get(tag);
        String payload = "";
        if (endPage%2 > 0 ){
            endPage += 1;
        }

        // 4ページ毎にデータを抽出
        for (int i = startPage; i <= endPage; i=i+4) {
            // Log.v(FLAG, "Count: " + i);
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
```

