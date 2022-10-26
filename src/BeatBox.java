import javax.sound.midi.*;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BeatBox {
    private static final int NUMBER_OF_BEATS = 16;
    private final List<BeatInstrument> instruments;

    private final Map<BeatInstrument, List<JCheckBox>> instrumentCheckboxes = new TreeMap<>();
    private JList<String> messages;
    private JTextField userMessage;

    private int nextNum;
    private String userName;
    private final Vector<String> incomingMessages = new Vector<>();
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final HashMap<String, boolean[]> otherSeqMap = new HashMap<>();

    private Sequencer sequencer;
    private Sequence sequence;
    private Track track;

    public static void main(String[] args) {
        new BeatBox().startUp(args[0]);
    }

    public BeatBox() {
        instruments = List.of(
                new BeatInstrument("Bass Drum", 35),
                new BeatInstrument("Closed Hi-Hat", 42),
                new BeatInstrument("Open Hi-Hat", 46),
                new BeatInstrument("Acoustic Snare", 38),
                new BeatInstrument("Crash Cymbal", 49),
                new BeatInstrument("Hand Clap", 39),
                new BeatInstrument("High Tom", 50),
                new BeatInstrument("Hi Bongo", 60),
                new BeatInstrument("Maracas", 70),
                new BeatInstrument("Whistle", 72),
                new BeatInstrument("Low Conga", 64),
                new BeatInstrument("Cowbell", 56),
                new BeatInstrument("Vibraslap", 58),
                new BeatInstrument("Low-mid Tom", 47),
                new BeatInstrument("High Agogo", 67),
                new BeatInstrument("Open Hi Conga", 63));
    }

    public void startUp(String name) {
        userName = name;
        try {
            Socket socket = new Socket("127.0.0.1", 3500);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(new RemoteReader());
        } catch (IOException ex) {
            System.out.println("Couldn't connect-you'll have to play alone.");
        }
        setUpMidi();
        buildGUI();
    }

    public void buildGUI() {
        JFrame frame = new JFrame("BeatBox");
        JPanel background = new JPanel(new BorderLayout());
        background.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        Box buttonBox = new Box(BoxLayout.Y_AXIS);
        JButton start = new JButton("Start");
        start.addActionListener(e -> buildTrackAndStart());
        buttonBox.add(start);

        JButton stop = new JButton("Stop");
        stop.addActionListener(e -> sequencer.stop());
        buttonBox.add(stop);

        JButton upTempo = new JButton("Tempo Up");
        upTempo.addActionListener(e -> changeTempo(1.03));
        buttonBox.add(upTempo);

        JButton downTempo = new JButton("Tempo Down");
        downTempo.addActionListener(e -> changeTempo(0.97));
        buttonBox.add(downTempo);

        JButton sendIt = new JButton("SendIT");
        sendIt.addActionListener(new MySendListener());
        buttonBox.add(sendIt);

        userMessage = new JTextField();

        buttonBox.add(userMessage);

        messages = new JList<>();
        messages.addListSelectionListener(new MyListSelectionListener());
        messages.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        buttonBox.add(new JScrollPane((messages)));
        messages.setListData(incomingMessages);

        Box nameBox = new Box(BoxLayout.Y_AXIS);
        GridLayout grid = new GridLayout(instruments.size(), NUMBER_OF_BEATS, 2, 1);
        JPanel gridPanel = new JPanel(grid);

        for (BeatInstrument instrument : instruments) {
            JLabel instrumentName = new JLabel(instrument.instrumentName);
            instrumentName.setBorder(BorderFactory.createEmptyBorder(4, 1, 4, 1));
            nameBox.add(instrumentName);

            List<JCheckBox> checkBoxList = new ArrayList<>();
            for (int i = 0; i < NUMBER_OF_BEATS; i++) {
                JCheckBox c = new JCheckBox();
                c.setSelected(false);
                checkBoxList.add(c);
                gridPanel.add(c);
            }
            instrumentCheckboxes.put(instrument, checkBoxList);
        }

        background.add(BorderLayout.EAST, buttonBox);
        background.add(BorderLayout.WEST, nameBox);
        background.add(BorderLayout.CENTER, gridPanel);

        frame.getContentPane().add(background);
        frame.setBounds(50, 50, 300, 300);
        frame.pack();
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private void changeTempo(double tempoMultiplayer) {
        float tempoFactor = sequencer.getTempoFactor();
        sequencer.setTempoFactor((float) (tempoFactor * tempoMultiplayer));
    }

    private void buildTrackAndStart() {
        sequence.deleteTrack(track);
        track = sequence.createTrack();

        for (Map.Entry<BeatInstrument, List<JCheckBox>> instrumentsToBeats : instrumentCheckboxes.entrySet()) {
            List<JCheckBox> checkBoxes = instrumentsToBeats.getValue();
            for (int i = 0; i < checkBoxes.size(); i++) {
                if (checkBoxes.get(i).isSelected()) {
                    BeatInstrument instrument = instrumentsToBeats.getKey();
                    track.add(makeEvent(ShortMessage.NOTE_ON, instrument.midiValue, 100, i));
                    track.add(makeEvent(ShortMessage.NOTE_OFF, instrument.midiValue, 100, i + 1));
                }
            }
        }
        track.add(makeEvent(ShortMessage.PROGRAM_CHANGE, 1, 0, 15)); // - so we always go to full 16 beats
        try {
            sequencer.setSequence(sequence);
            sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
            sequencer.start();
            sequencer.setTempoInBPM(120);
        } catch (InvalidMidiDataException e) {
            e.printStackTrace();
        }
    }

    private MidiEvent makeEvent(int command, int one, int two, int tick) {
        try {
            ShortMessage midiMessage = new ShortMessage(command, 9, one, two);
            return new MidiEvent(midiMessage, tick);
        } catch (InvalidMidiDataException ignored) {
            return null;
        }
    }

    private void setUpMidi() {
        try {
            sequencer = MidiSystem.getSequencer();
            sequencer.open();
            sequence = new Sequence(Sequence.PPQ, 4);
            track = sequence.createTrack();
            sequencer.setTempoInBPM(120);
        } catch (InvalidMidiDataException | MidiUnavailableException e) {
            e.printStackTrace();
        }
    }

    private class MyListSelectionListener implements ListSelectionListener {
        @Override
        public void valueChanged(ListSelectionEvent le) {
            if (!le.getValueIsAdjusting()) {
                String selected = messages.getSelectedValue();
                if (selected != null) {
                    // now go to the map, and change the sequence
                    boolean[] selectedState = otherSeqMap.get(selected);
                    changeSequence(selectedState);
                    sequencer.stop();
                    buildTrackAndStart();
                }
            }
        }
    }

    private void changeSequence(boolean[] newCheckboxStates) {
        int i = 0;
        for (List<JCheckBox> checkBoxesForInstrument : instrumentCheckboxes.values()) {
            for (JCheckBox checkBox : checkBoxesForInstrument) {
                checkBox.setSelected(newCheckboxStates[i++]);
            }
        }
    }

    public class RemoteReader implements Runnable {
        @Override
        public void run() {
            try {
                Object obj;
                while ((obj = in.readObject()) != null) {
                    System.out.println("got an object from server");
                    String nameToShow = (String) obj;
                    boolean[] checkboxState = (boolean[]) in.readObject();
                    otherSeqMap.put(nameToShow, checkboxState);
                    incomingMessages.add(nameToShow);
                    messages.setListData(incomingMessages);
                }
            } catch (IOException | ClassNotFoundException ex) {
                ex.printStackTrace();
            }
        }
    }

    public class MySendListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent event) {
            //State of check boxes
            boolean[] checkBoxState = new boolean[256];

            int i = 0;
            for (List<JCheckBox> instrumentCheckBoxes : instrumentCheckboxes.values()) {
                for (JCheckBox instrumentCheckBox : instrumentCheckBoxes) {
                    checkBoxState[i++] = instrumentCheckBox.isSelected();
                }
            }
            try {
                out.writeObject(userName + nextNum++ + ": " + userMessage.getText());
                out.writeObject(checkBoxState);
            } catch (IOException e) {
                System.out.println("Terribly sorry. Could not send it to the server.");
                e.printStackTrace();
            }
            userMessage.setText("");
        }
    }

    private record BeatInstrument(String instrumentName, int midiValue) implements Comparable<BeatInstrument> {
        @Override
        public int compareTo(BeatInstrument beatInstrument) {
            return instrumentName.compareTo(beatInstrument.instrumentName);
        }
    }
}
