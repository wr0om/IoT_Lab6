package com.example.tutorial6;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    public TerminalFragment() {
    }

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;

    private TextView tv_estimated_steps;
    private TextView receiveText;
    private TextView sendText;
    private TextUtil.HexWatcher hexWatcher;

    private EditText et_receive_steps;
    private EditText et_receive_filename;
    private Button btn_start;
    private Button btn_stop;
    private Button btn_reset;
    private Button btn_save;


    private Spinner dropdown;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;
    private boolean to_receive = false;

    LineChart mpLineChart;
    LineDataSet lineDataSet;

    ArrayList<ILineDataSet> dataSets = new ArrayList<>();
    LineData data;
    private float starting_time = 0;
    private boolean receiveTime;
    private PyObject pyobj;
    private String est_steps = "0";

    private static final String filenames_csv = "/sdcard/csv_dir/filenames.csv";
    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");

        if (!Python.isStarted()){
            Python.start(new AndroidPlatform(getContext()));
        }

        Python py = Python.getInstance();
        pyobj = py.getModule("test");

    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());
        tv_estimated_steps = view.findViewById(R.id.tv_estimated_steps);


        sendText = view.findViewById(R.id.send_text);
        hexWatcher = new TextUtil.HexWatcher(sendText);
        hexWatcher.enable(hexEnabled);
        sendText.addTextChangedListener(hexWatcher);
        sendText.setHint(hexEnabled ? "HEX mode" : "");

        View sendBtn = view.findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));


        et_receive_steps = view.findViewById(R.id.et_receive_steps);
        et_receive_filename = view.findViewById(R.id.et_receive_filename);
        btn_start = view.findViewById(R.id.btn_start);
        btn_start.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(getContext(),"Start",Toast.LENGTH_SHORT).show();
                to_receive = true;
                receiveTime = true;

            }
        });
        btn_stop = view.findViewById(R.id.btn_stop);
        btn_stop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(getContext(),"Stop",Toast.LENGTH_SHORT).show();
                to_receive = false;

            }
        });
        btn_reset = view.findViewById(R.id.btn_reset);
        btn_reset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(getContext(),"Reset",Toast.LENGTH_SHORT).show();
                File f = new File("/sdcard/csv_dir/data.csv");
                to_receive=false;
                clear_graph();
                est_steps = "0";
                update_est_steps();



                if(f.delete())
                    Toast.makeText(getContext(), "RESET COMPLETE", Toast.LENGTH_SHORT).show();

            }
        });
        btn_save = view.findViewById(R.id.btn_save);
        btn_save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(getContext(),"Save",Toast.LENGTH_SHORT).show();
                String steps_str = et_receive_steps.getText().toString();
                String filename = et_receive_filename.getText().toString();
                String ect_type = dropdown.getSelectedItem().toString();

                if(steps_str.isEmpty() || filename.isEmpty() || ect_type.isEmpty()) {
                    Toast.makeText(getContext(), "NOT ENTERED ALL VALUES", Toast.LENGTH_SHORT).show();
                    return;
                }


                File file = new File("/sdcard/csv_dir/");
                file.mkdirs();

                String csv = "/sdcard/csv_dir/" + filename + ".csv";
                try {
                    CSVWriter csvWriter = new CSVWriter(new FileWriter(csv,true));
                    String[] first_row = new String[]{"NAME:", filename + ".csv"};
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm");
                    String currentDateandTime = sdf.format(new Date());
                    String[] second_row = new String[]{"EXPERIMENT TIME:", currentDateandTime};
                    String[] third_row = new String[]{"ACTIVITY TYPE:", ect_type};
                    String[] fourth_row = new String[]{"COUNT OF ACTUAL STEPS:", steps_str};
                    String[] fifth_row = new String[]{"ESTIMATED NUMBER OF STEPS:", est_steps};//CHANGE STEPS HERE

                    String[] empty_row = new String[]{};
                    csvWriter.writeNext(first_row);
                    csvWriter.writeNext(second_row);
                    csvWriter.writeNext(third_row);
                    csvWriter.writeNext(fourth_row);
                    csvWriter.writeNext(fifth_row);
                    csvWriter.writeNext(empty_row);
                    copy_csv(csvWriter);
                    csvWriter.close();


                    CSVWriter csvWriter2 = new CSVWriter(new FileWriter(filenames_csv,true));
                    csvWriter2.writeNext(new String[]{filename});
                    csvWriter2.close();
                    File f = new File("/sdcard/csv_dir/data.csv");

                    clear_graph();
                    est_steps = "0";
                    update_est_steps();
                    if(f.delete())
                        Toast.makeText(getContext(), "SAVE COMPLETE", Toast.LENGTH_SHORT).show();


                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        String[] paths = new String[]{"Walking", "Running"};
        dropdown = (Spinner)view.findViewById(R.id.spinner);
        ArrayAdapter<String>adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item, paths);
        dropdown.setAdapter(adapter);



        mpLineChart = (LineChart) view.findViewById(R.id.line_chart);
        lineDataSet =  new LineDataSet(emptyDataValues(), "N");
        lineDataSet.setColor(Color.RED);
        lineDataSet.setDrawCircles(false);

        dataSets.add(lineDataSet);

        data = new LineData(dataSets);
        mpLineChart.setData(data);
        mpLineChart.invalidate();

        Button buttonClear = (Button) view.findViewById(R.id.button1);
        Button buttonCsvShow = (Button) view.findViewById(R.id.button2);


        buttonClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(getContext(),"Clear",Toast.LENGTH_SHORT).show();
                LineData data = mpLineChart.getData();
                ILineDataSet set = data.getDataSetByIndex(0);
                data.getDataSetByIndex(0);
                while(set.removeLast()){}

            }
        });

        buttonCsvShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OpenLoadCSV();

            }
        });

        return view;
    }

    private void clear_graph() {
        lineDataSet.clear();
        data.notifyDataChanged();
        mpLineChart.invalidate();
    }

    private void copy_csv(CSVWriter csvWriter) {
        String[] header = new String[]{"Time [sec]", "N"};
        csvWriter.writeNext(header);
        String path = "/sdcard/csv_dir/data.csv";
        try {
            File file = new File(path);
            CSVReader reader = new CSVReader(new FileReader(file));
            String[]nextline;
            while((nextline = reader.readNext())!= null){
                if(nextline != null){
                    csvWriter.writeNext(nextline);
                }
            }

        }catch (Exception e){}
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
        menu.findItem(R.id.hex).setChecked(hexEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled;
            sendText.setText("");
            hexWatcher.enable(hexEnabled);
            sendText.setHint(hexEnabled ? "HEX mode" : "");
            item.setChecked(hexEnabled);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private String[] clean_str(String[] stringsArr){
         for (int i = 0; i < stringsArr.length; i++)  {
             stringsArr[i] = stringsArr[i].replaceAll(" ","");
        }


        return stringsArr;
    }
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if(hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn);
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] message) {
        if (!to_receive)
            return;
        if (hexEnabled) {
            receiveText.append(TextUtil.toHexString(message) + '\n');
        } else {
            String msg = new String(message);
            if(newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
                // don't show CR as ^M if directly before LF
                String msg_to_save = msg;
                msg_to_save = msg.replace(TextUtil.newline_crlf, TextUtil.emptyString);


                // check message length
                if (msg_to_save.length() > 1){
                // split message string by ',' char
                String[] parts = msg_to_save.split(",");
                // function to trim blank spaces
                parts = clean_str(parts);

                // saving data to csv
                try {

                    // create new csv unless file already exists
                    File file = new File("/sdcard/csv_dir/");
                    file.mkdirs();
                    String csv = "/sdcard/csv_dir/data.csv";
                    CSVWriter csvWriter = new CSVWriter(new FileWriter(csv,true));

                    if (receiveTime) {
                        receiveTime = false;
                        starting_time = Float.parseFloat(parts[0]);
                    }
                    parts[0] = Float.toString((Float.parseFloat(parts[0]) - starting_time) / 1000);
                    // parse string values, in this case [0] is tmp & [1] is N
                    String row[]= new String[]{parts[0],parts[1]};

                    csvWriter.writeNext(row);
                    csvWriter.close();

                    // add received values to line dataset for plotting the linechart
                    data.addEntry(new Entry(Float.parseFloat(parts[0]),Float.parseFloat(parts[1])),0);
                    lineDataSet.notifyDataSetChanged(); // let the data know a dataSet changed

                    List<Entry> points = lineDataSet.getValues();
                    Float[] t_arr = new Float[points.size()];
                    Float[] N_arr = new Float[points.size()];
                    for(int i = 0; i < points.size();i++){
                        Entry curr = points.get(i);
                        t_arr[i] = curr.getX();
                        N_arr[i] = curr.getY();
                    }
                    PyObject obj = pyobj.callAttr("identify_peek", t_arr, N_arr);
                    est_steps = obj.toString();
                    update_est_steps();

                    mpLineChart.notifyDataSetChanged(); // let the chart know it's data changed
                    mpLineChart.invalidate(); // refresh


                } catch (IOException e) {

                    e.printStackTrace();
                }}

                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                // send msg to function that saves it to csv
                // special handling if CR and LF come in separate fragments
                if (pendingNewline && msg.charAt(0) == '\n') {
                    Editable edt = receiveText.getEditableText();
                    if (edt != null && edt.length() > 1)
                        edt.replace(edt.length() - 2, edt.length(), "");
                }
                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }
            receiveText.append(TextUtil.toCaretString(msg, newline.length() != 0));
        }
    }

    private void update_est_steps() {
        tv_estimated_steps.setText("ESTIMATED NUMBER OF STEPS: " + est_steps);
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        try {
        receive(data);}
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

    private ArrayList<Entry> emptyDataValues()
    {
        ArrayList<Entry> dataVals = new ArrayList<Entry>();
        return dataVals;
    }

    private void OpenLoadCSV(){
        Intent intent = new Intent(getContext(),LoadCSV.class);
        startActivity(intent);
    }

}
