package com.integriscreen.keystrokedetector;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.icu.text.AlphabeticIndex;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private LineChart fftChart;
    private LineDataSet fftData;
    private LineData data;
    private int MAX_PLOT_VALS = 250;
    private int plot_count = 1;
    public int last_event_window = 0;
    public ArrayList<Double> energySerie = new ArrayList<>();
    public double thresholdValue = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkRecordPermission();

        Button bgNoiseButton = (Button) findViewById(R.id.noise);
        bgNoiseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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
            }
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

        new Sampler(this).start();
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

    private void checkRecordPermission() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    123);
        }
    }

}
