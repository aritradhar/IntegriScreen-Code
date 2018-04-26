package com.integriscreen.keystrokedetector;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private LineChart fftChart;
    private LineDataSet fftData;
    private LineData data;
    private int MAX_PLOT_VALS = 250;
    private int plot_count = 1;
    public int last_event_window = 0;
    public ArrayList<Double> energySerie = new ArrayList<>();
    public double thresholdValue = -1;
//    public Evaluator clf;
//    public Map<FieldName, FieldValue> clfIn;
//    List<Integer> fields = new ArrayList<>();
    public RequestQueue queue;
    public String url = "http://tildem.inf.ethz.ch:1731/predict";
    private int keystrokeCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissions();

        queue = Volley.newRequestQueue(this);

        Button bgNoiseButton = findViewById(R.id.noise);
        bgNoiseButton.setOnClickListener(view -> {
            // how many samples we require for the msecs of bg noise
            Double[] bgNoiseSerie;
            if (energySerie.size() >= RecordingParameters.BG_SAMPLES){
                // copy last sample
                bgNoiseSerie = energySerie.subList(energySerie.size() - RecordingParameters.BG_SAMPLES, energySerie.size()).toArray(new Double[RecordingParameters.BG_SAMPLES]);
            }
            else {
                // get up to as many as there are
                bgNoiseSerie = energySerie.toArray(new Double[energySerie.size()]);
            }
            // calculate average of the window, set it as bg noise level threshold
            thresholdValue = Arrays.stream(bgNoiseSerie).mapToDouble(Double::doubleValue).average().getAsDouble();
            ((TextView) findViewById(R.id.threshold)).setText(String.valueOf(thresholdValue));
        });

        fftChart = findViewById(R.id.chart);
        fftChart.getAxisLeft().setAxisMaximum(60f); // the axis maximum is 100
        // fftChart.setAutoScaleMinMaxEnabled(Boolean.TRUE);

        fftData = new LineDataSet(new ArrayList<Entry>(), "x");
        // fftData.setAxisDependency(YAxis.AxisDependency.LEFT);
        fftData.setColor(Color.RED);
        fftData.addEntry(new Entry(0, 0));

        List<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(fftData);

        data = new LineData(dataSets);
        fftChart.setData(data);
        // fftChart.invalidate();

        // Load the keystroke classifier

//        AssetManager assetManager = getAssets();
//
//        try {
//            InputStream is = assetManager.open("clf.ser");
//            clf = EvaluatorUtil.createEvaluator(is);
//            clf.verify();
//        }
//        catch (Exception e) {
//            Log.e("ModelLoad", "I kinda failed", e);
//
//            // yeah whatever stop complaining Java
//        }
//
//        clfIn = new LinkedHashMap<>();
//        List<InputField> inputFields = clf.getInputFields();
//        Log.i("ML FIELDS LEN", Integer.toString(inputFields.size()));
//        for(InputField inputField : inputFields){
//            FieldName inputFieldName = inputField.getName();
//            fields.add(Integer.parseInt(inputFieldName.toString().substring(1)));
//            // The raw value is passed through: 1) outlier treatment, 2) missing value treatment, 3) invalid value treatment and 4) type conversion
//            FieldValue inputFieldValue = inputField.prepare(0);
//
//            clfIn.put(inputFieldName, inputFieldValue);
//        }

        new Sampler(this).start();
    }

    public void isKeystroke(float[] sample, int timestamp) {
        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                response -> {
                    Log.i("NET COMM", response);
                    if (Integer.parseInt(response) == 0) {
                        keystrokeCount += 1;
                        runOnUiThread(() -> ((Button) findViewById(R.id.count)).setText(String.valueOf(keystrokeCount)));
                        // TODO record time somehow
                    }
                },
                error -> {
                }) {
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("v", Arrays.toString(sample));
                return params;
            }
        };

        queue.add(stringRequest);
    }

    public void updateXChart(double value) {
        runOnUiThread(() -> {
            fftData.addEntry(new Entry(plot_count, (float) value));
            plot_count += 1;
            if (plot_count > MAX_PLOT_VALS) {
                fftData.removeFirst();
            }
            data.notifyDataChanged(); // let the data know a dataSet changed
            fftChart.notifyDataSetChanged(); // let the chart know it's data changed
            fftChart.invalidate();
        });
    }

    private void checkPermissions() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    123);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET},
                    456);
        }
    }

}
