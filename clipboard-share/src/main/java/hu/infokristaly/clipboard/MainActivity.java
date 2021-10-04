package hu.infokristaly.clipboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import example.zxing.R;

public class MainActivity extends AppCompatActivity {

    public final int CUSTOMIZED_REQUEST_CODE = 0x0000ffff;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    public ClipData clipDataOnResume;

    /*
     * from Android 10 clipboard content accessible only from window focus change
     * https://stackoverflow.com/questions/30129984/how-to-get-the-onwindowfocuschanged-on-fragment
     *
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            this.clipDataOnResume = clipboard.getPrimaryClip();
        }
    }

    static class SendContentTask extends AsyncTask<String, Void, Integer> {

        private Exception exception;

        protected Integer doInBackground(@NonNull String... args) {
            String url = args[0];
            String clipboardContent = args[1];
            Integer result;
            try {
                CloseableHttpClient client = HttpClients.createDefault();
                HttpPost httpPost = new HttpPost(url);

                List<NameValuePair> params = new ArrayList<NameValuePair>();
                params.add(new BasicNameValuePair("content", clipboardContent));
                if (url.endsWith("jsonpost")) {
                    JSONObject json = new JSONObject();
                    json.put("content", clipboardContent);
                    StringEntity requestEntity = new StringEntity(json.toString(),
                            ContentType.APPLICATION_JSON);
                } else if (url.endsWith("post")) {
                    httpPost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
                }
                CloseableHttpResponse response = client.execute(httpPost);
                result = response.getStatusLine().getStatusCode();
                client.close();

            } catch (Exception e) {
                this.exception = e;
                result = 500;
            } finally {
            }
            return result;
        }

        protected void onPostExecute() {
        }
    }

    static class GetContentTask extends AsyncTask<String, Void, String> {

        private Exception exception;
        private MainActivity activity;

        public GetContentTask(MainActivity activity) {
            this.activity = activity;
        }

        protected String doInBackground(@NonNull String... args) {
            String url = args[0];
            String result;
            try {
                CloseableHttpClient client = HttpClients.createDefault();
                HttpGet httpPost = new HttpGet(url);

                CloseableHttpResponse response = client.execute(httpPost);

                HttpEntity entity =response.getEntity();
                result = EntityUtils.toString(entity, "UTF-8");

                client.close();
            } catch (Exception e) {
                this.exception = e;
                result = "";
            } finally {
            }
            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(null,result));
        }
    }

    /**
     * Sample of scanning from a Fragment
     */
    public static class ScanFragment extends Fragment {
        private String toast;

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            displayToast();
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_scan, container, false);
            Button scan = view.findViewById(R.id.scan_from_fragment);
            scan.setOnClickListener(v -> scanFromFragment());
            return view;
        }

        public void scanFromFragment() {
            IntentIntegrator.forSupportFragment(this).initiateScan();
        }

        private void displayToast() {
            if(getActivity() != null && toast != null) {
                Toast.makeText(getActivity(), toast, Toast.LENGTH_LONG).show();
                toast = null;
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            if(result != null) {
                if(result.getContents() == null) {
                    toast = "Cancelled from fragment";
                } else {
                    toast = "Location: " + result.getContents();
                    if (result.getContents().endsWith("post")) {
                        ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                        String clipContent = "";
                        MainActivity activity = ((MainActivity) getActivity());
                        if (clipboard.hasPrimaryClip()) {
                            ClipData clipData = clipboard.getPrimaryClip();
                            ClipData.Item item = clipData.getItemAt(0);
                            clipContent = item.getText().toString();
                        } else if (activity.clipDataOnResume != null && activity.clipDataOnResume.getItemCount() > 0){
                            ClipData.Item item = activity.clipDataOnResume.getItemAt(0);
                            clipContent = item.getText().toString();
                        }
                        new SendContentTask().execute(result.getContents(), clipContent);
                    } else if (result.getContents().endsWith("get")) {
                        new GetContentTask((MainActivity) getActivity()).execute(result.getContents());
                    }
                }

                // At this point we may or may not have a reference to the activity
                displayToast();
            }
        }
    }
}
