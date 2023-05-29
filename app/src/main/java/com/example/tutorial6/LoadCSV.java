package com.example.tutorial6;


import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import java.util.List;




public class LoadCSV extends AppCompatActivity {
    private Spinner dropdown;
    private Button btn_delete;
    private TextView tv_estimated_steps;
    private static final String filenames_csv = "/sdcard/csv_dir/filenames.csv";
    String[] filenames_list;


    private String[] update_spinner(){
        File file = new File("/sdcard/csv_dir/");
        file.mkdirs();
        ArrayList<String[]> filenames = new ArrayList<>();
        filenames = CsvRead(filenames_csv);
        String[] filenames_list = new String[filenames.size()];
        for(int i = 0; i < filenames.size(); i++)
            filenames_list[i] = filenames.get(i)[0];
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, filenames_list);
        dropdown.setAdapter(adapter);
        return filenames_list;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_csv);
        Button BackButton = (Button) findViewById(R.id.button_back);
        LineChart lineChart = (LineChart) findViewById(R.id.line_chart);
        tv_estimated_steps = findViewById(R.id.tv_estimated_steps);


        dropdown = findViewById(R.id.spinner);


        filenames_list = update_spinner();

        dropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {

                lineChart.clear();
                ArrayList<String[]> csvData = new ArrayList<>();
                String file_name_selected = filenames_list[position];
                csvData= CsvRead("/sdcard/csv_dir/" + file_name_selected + ".csv");
                LineDataSet lineDataSet =  new LineDataSet(DataValues(csvData),"N");
                lineDataSet.setColor(Color.RED);
                lineDataSet.setDrawCircles(false);

                ArrayList<ILineDataSet> dataSets = new ArrayList<>();
                dataSets.add(lineDataSet);


                LineData data = new LineData(dataSets);
                lineChart.setData(data);
                lineChart.invalidate();

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }

        });

        btn_delete = findViewById(R.id.btn_delete);
        btn_delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String filename_delete = dropdown.getSelectedItem().toString();
                Toast.makeText(LoadCSV.this, "yay", Toast.LENGTH_SHORT).show();

                File f1 = new File("/sdcard/csv_dir/" + filename_delete + ".csv");
                if(f1.delete()) {
                    Toast.makeText(LoadCSV.this, "Deleted " + filename_delete, Toast.LENGTH_SHORT).show();
                    File f2 = new File(filenames_csv);
                    if(f2.delete()){
                        try {
                            CSVWriter csvWriter2 = new CSVWriter(new FileWriter(filenames_csv, true));
                            for (String fn : filenames_list) {
                                if (fn.equals(filename_delete))
                                    continue;
                                csvWriter2.writeNext(new String[]{fn});
                            }
                            csvWriter2.close();
                            filenames_list = update_spinner();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                }


            }
        });

        BackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClickBack();
            }
        });
    }

    private void ClickBack(){
        finish();

    }

    private ArrayList<String[]> CsvRead(String path){
        ArrayList<String[]> CsvData = new ArrayList<>();
        try {
            File file = new File(path);
            CSVReader reader = new CSVReader(new FileReader(file));
            String[]nextline;
            while((nextline = reader.readNext())!= null){
                if(nextline != null){
                    CsvData.add(nextline);

                }
            }

        }catch (Exception e){}
        return CsvData;
    }

    private ArrayList<Entry> DataValues(ArrayList<String[]> csvData){
        // if xyz == 0 - then x
        // else if xyz == 1 then y
        // else if xyz == 2 then z
        ArrayList<Entry> dataVals = new ArrayList<Entry>();
        for (int i = 0; i < csvData.size(); i++){

            if(i == 4){
                tv_estimated_steps.setText("ESTIMATED NUMBER OF STEPS: " + csvData.get(i)[1].toString());
            }
            if (i <= 6)
                continue;
            dataVals.add(new Entry(Float.parseFloat(csvData.get(i)[0]),
                    Float.parseFloat(csvData.get(i)[1])));

        }

        return dataVals;
    }

}