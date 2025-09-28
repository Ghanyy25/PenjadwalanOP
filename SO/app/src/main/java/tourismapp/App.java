package tourismapp;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;

import java.util.*;
import java.util.stream.Collectors;

public class App extends Application {

    // Record untuk menyimpan data proses
    public static class Process {
        private String name;
        private int arrivalTime;
        private int burstTime;
        private int remainingBurstTime;
        private int completionTime;
        private int turnaroundTime;
        private int waitingTime;
        private final Color color;

        public Process(String name, int arrivalTime, int burstTime, Color color) {
            this.name = name;
            this.arrivalTime = arrivalTime;
            this.burstTime = burstTime;
            this.remainingBurstTime = burstTime;
            this.color = color;
        }

        // Getters & Setters untuk TableView Input
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getArrivalTime() { return arrivalTime; }
        public void setArrivalTime(int arrivalTime) { this.arrivalTime = arrivalTime; }
        public int getBurstTime() { return burstTime; }
        public void setBurstTime(int burstTime) { this.burstTime = burstTime; this.remainingBurstTime = burstTime; }

        // Getters untuk TableView Output
        public int getCompletionTime() { return completionTime; }
        public int getTurnaroundTime() { return turnaroundTime; }
        public int getWaitingTime() { return waitingTime; }

        // Reset status proses
        public void reset() {
            this.remainingBurstTime = this.burstTime;
            this.completionTime = 0;
            this.turnaroundTime = 0;
            this.waitingTime = 0;
        }
    }

    private record GanttBlock(String processName, int start, int end) {}

    private record SimulationResult(List<Process> finalProcesses, List<GanttBlock> ganttChart, double awt, double atat) {}

    // --- LOGIKA ALGORITMA PENJADWALAN ---
    private SimulationResult runStandardRoundRobin(List<Process> processes, int quantum) {
        List<Process> processList = processes.stream()
                .map(p -> new Process(p.name, p.arrivalTime, p.burstTime, p.color))
                .sorted(Comparator.comparingInt(p -> p.arrivalTime))
                .collect(Collectors.toList());
        
        Queue<Process> readyQueue = new LinkedList<>();
        List<GanttBlock> ganttChart = new ArrayList<>();
        int currentTime = 0;
        int completedProcesses = 0;
        List<Process> jobQueue = new ArrayList<>(processList);

        while (completedProcesses < processList.size()) {
            Iterator<Process> iterator = jobQueue.iterator();
            while(iterator.hasNext()){
                Process p = iterator.next();
                if(p.arrivalTime <= currentTime){
                    readyQueue.add(p);
                    iterator.remove();
                }
            }

            if (readyQueue.isEmpty()) {
                if (jobQueue.isEmpty()) break;
                currentTime++;
                continue;
            }

            Process currentProcess = readyQueue.poll();
            int startTime = currentTime;
            int executionTime = Math.min(quantum, currentProcess.remainingBurstTime);
            
            currentTime += executionTime;
            currentProcess.remainingBurstTime -= executionTime;
            
            ganttChart.add(new GanttBlock(currentProcess.name, startTime, currentTime));

            iterator = jobQueue.iterator();
            while(iterator.hasNext()){
                Process p = iterator.next();
                if(p.arrivalTime <= currentTime){
                    readyQueue.add(p);
                    iterator.remove();
                }
            }
            
            if (currentProcess.remainingBurstTime > 0) {
                readyQueue.add(currentProcess);
            } else {
                completedProcesses++;
                currentProcess.completionTime = currentTime;
                currentProcess.turnaroundTime = currentProcess.completionTime - currentProcess.arrivalTime;
                currentProcess.waitingTime = currentProcess.turnaroundTime - currentProcess.burstTime;
            }
        }
        
        double awt = processList.stream().mapToInt(p -> p.waitingTime).average().orElse(0.0);
        double atat = processList.stream().mapToInt(p -> p.turnaroundTime).average().orElse(0.0);

        return new SimulationResult(processList, ganttChart, awt, atat);
    }
    
    private SimulationResult runEnhancedRoundRobin(List<Process> processes, int quantum) {
        List<Process> processList = processes.stream()
                .map(p -> new Process(p.name, p.arrivalTime, p.burstTime, p.color))
                .sorted(Comparator.comparingInt(p -> p.arrivalTime))
                .collect(Collectors.toList());

        List<GanttBlock> ganttChart = new ArrayList<>();
        int currentTime = 0;
        
        Set<Process> executedInFirstCycle = new HashSet<>();
        Queue<Process> readyQueue = new LinkedList<>();
        List<Process> jobQueue = new ArrayList<>(processList);
        
        while(executedInFirstCycle.size() < processList.size()){
            Iterator<Process> iterator = jobQueue.iterator();
            while(iterator.hasNext()){
                Process p = iterator.next();
                if(p.arrivalTime <= currentTime && !readyQueue.contains(p)){
                    readyQueue.add(p);
                    iterator.remove();
                }
            }
            
            if(readyQueue.isEmpty()){
                if (jobQueue.isEmpty() || processList.stream().allMatch(p -> executedInFirstCycle.contains(p))) break;
                currentTime++;
                continue;
            }
            
            Process currentProcess = readyQueue.poll();
            int startTime = currentTime;
            int executionTime = Math.min(quantum, currentProcess.remainingBurstTime);
            currentTime += executionTime;
            currentProcess.remainingBurstTime -= executionTime;
            ganttChart.add(new GanttBlock(currentProcess.name, startTime, currentTime));
            
            executedInFirstCycle.add(currentProcess);

            if (currentProcess.remainingBurstTime <= 0) {
                currentProcess.completionTime = currentTime;
            }
        }

        int doubledQuantum = quantum * 2;
        List<Process> remainingProcesses = processList.stream()
                .filter(p -> p.remainingBurstTime > 0)
                .sorted(Comparator.comparingInt(p -> p.remainingBurstTime))
                .collect(Collectors.toList());

        for (Process p : remainingProcesses) {
            while (p.remainingBurstTime > 0) {
                int startTime = currentTime;
                int executionTime = Math.min(doubledQuantum, p.remainingBurstTime);
                currentTime += executionTime;
                p.remainingBurstTime -= executionTime;
                ganttChart.add(new GanttBlock(p.name, startTime, currentTime));
            }
            p.completionTime = currentTime;
        }

        for(Process p : processList){
            p.turnaroundTime = p.completionTime - p.arrivalTime;
            p.waitingTime = p.turnaroundTime - p.burstTime;
        }
        
        double awt = processList.stream().mapToInt(p -> p.waitingTime).average().orElse(0.0);
        double atat = processList.stream().mapToInt(p -> p.turnaroundTime).average().orElse(0.0);

        return new SimulationResult(processList, ganttChart, awt, atat);
    }
    
    // --- UI JAVAFX ---
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Simulasi Penjadwalan Round Robin");

        // --- Bagian Input ---
        VBox inputSection = new VBox(10);
        inputSection.setPadding(new Insets(15));
        
        TableView<Process> processTable = createProcessInputTable();
        
        HBox tableButtons = new HBox(10);
        Button addBtn = new Button("Tambah Proses");
        Button removeBtn = new Button("Hapus Proses");
        tableButtons.getChildren().addAll(addBtn, removeBtn);
        addBtn.setOnAction(e -> {
            int newIndex = processTable.getItems().size();
            processTable.getItems().add(new Process("P"+(newIndex+1), 0, 0, Color.GRAY));
        });
        removeBtn.setOnAction(e -> {
            Process selected = processTable.getSelectionModel().getSelectedItem();
            if(selected != null) processTable.getItems().remove(selected);
        });

        Label methodLabel = new Label("Pilih Metode Penjadwalan:");
        ComboBox<String> methodSelector = new ComboBox<>();
        methodSelector.getItems().addAll("Round Robin Umum (Standar)", "Pengembangan Round Robin (Modifikasi)");
        methodSelector.setValue("Round Robin Umum (Standar)");

        Label quantumLabel = new Label("Time Quantum (q):");
        TextField quantumField = new TextField("3");
        quantumField.setPrefWidth(50);
        
        Button simulateBtn = new Button("JALANKAN SIMULASI");
        simulateBtn.setStyle("-fx-font-weight: bold;");

        inputSection.getChildren().addAll(
            new Label("1. Input Proses"), processTable, tableButtons, new Separator(),
            new Label("2. Konfigurasi Simulasi"), methodLabel, methodSelector, quantumLabel, quantumField, new Separator(),
            simulateBtn
        );
        
        // --- Bagian Output ---
        VBox outputSection = new VBox(10);
        outputSection.setPadding(new Insets(15));
        
        Label outputTitle = new Label("3. Hasil Simulasi");
        TableView<Process> outputTable = createProcessOutputTable();
        Label statsLabel = new Label("AWT: - | ATAT: -");
        statsLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        Label ganttLabel = new Label("Urutan Eksekusi (Gantt Chart):");
        TextArea ganttArea = new TextArea();
        ganttArea.setEditable(false);
        ganttArea.setFont(Font.font("Monospaced"));

        outputSection.getChildren().addAll(outputTitle, outputTable, statsLabel, ganttLabel, ganttArea);
        
        // --- Aksi Tombol Simulasi ---
        simulateBtn.setOnAction(e -> {
            try {
                int quantum = Integer.parseInt(quantumField.getText());
                String selectedMethod = methodSelector.getValue();
                if (quantum <= 0) throw new NumberFormatException();

                processTable.getItems().forEach(Process::reset);
                
                SimulationResult result;
                if (selectedMethod.equals("Round Robin Umum (Standar)")) {
                    result = runStandardRoundRobin(new ArrayList<>(processTable.getItems()), quantum);
                } else {
                    result = runEnhancedRoundRobin(new ArrayList<>(processTable.getItems()), quantum);
                }

                outputTable.setItems(FXCollections.observableArrayList(result.finalProcesses()));
                statsLabel.setText(String.format("Average Waiting Time (AWT): %.2f  |  Average Turnaround Time (ATAT): %.2f", result.awt(), result.atat()));

                
                StringBuilder ganttText = new StringBuilder();
                ganttText.append("Waktu Mulai -> Selesai :: Proses\n");
                ganttText.append("----------------------------------\n");
                
                for (GanttBlock block : result.ganttChart()) {
                    // Menggunakan String.format() agar formatnya rapi dan sejajar
                    ganttText.append(String.format("    %2d -> %-5d :: %s\n", 
                        block.start(), block.end(), block.processName()));
                }
                ganttArea.setText(ganttText.toString());
                
            } catch (NumberFormatException ex) {
                new Alert(Alert.AlertType.ERROR, "Time Quantum harus berupa angka positif.").showAndWait();
            }
        });

        // --- Tata Letak Utama ---
        HBox root = new HBox(10);
        root.setPadding(new Insets(10));
        root.getChildren().addAll(inputSection, new Separator(javafx.geometry.Orientation.VERTICAL), outputSection);
        HBox.setHgrow(outputSection, Priority.ALWAYS);

        Scene scene = new Scene(root, 1000, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private TableView<Process> createProcessInputTable() {
        TableView<Process> table = new TableView<>();
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Process, String> nameCol = new TableColumn<>("Proses");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Process, Integer> atCol = new TableColumn<>("Arrival Time");
        atCol.setCellValueFactory(new PropertyValueFactory<>("arrivalTime"));
        atCol.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        atCol.setOnEditCommit(e -> e.getTableView().getItems().get(e.getTablePosition().getRow()).setArrivalTime(e.getNewValue()));

        TableColumn<Process, Integer> btCol = new TableColumn<>("Burst Time");
        btCol.setCellValueFactory(new PropertyValueFactory<>("burstTime"));
        btCol.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        btCol.setOnEditCommit(e -> e.getTableView().getItems().get(e.getTablePosition().getRow()).setBurstTime(e.getNewValue()));

        table.getColumns().addAll(nameCol, atCol, btCol);
        
        Color[] colors = {Color.DODGERBLUE, Color.ORANGERED, Color.SEAGREEN, Color.GOLD, Color.MEDIUMPURPLE};
        table.setItems(FXCollections.observableArrayList(
            new Process("P1", 0, 12, colors[0]), new Process("P2", 2, 8, colors[1]),
            new Process("P3", 3, 5, colors[2]), new Process("P4", 5, 2, colors[3]),
            new Process("P5", 9, 1, colors[4])
        ));
        return table;
    }

    private TableView<Process> createProcessOutputTable() {
        TableView<Process> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Process, String> nameCol = new TableColumn<>("Proses");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Process, Integer> ctCol = new TableColumn<>("Completion Time");
        ctCol.setCellValueFactory(new PropertyValueFactory<>("completionTime"));

        TableColumn<Process, Integer> tatCol = new TableColumn<>("Turnaround Time");
        tatCol.setCellValueFactory(new PropertyValueFactory<>("turnaroundTime"));

        TableColumn<Process, Integer> wtCol = new TableColumn<>("Waiting Time");
        wtCol.setCellValueFactory(new PropertyValueFactory<>("waitingTime"));

        table.getColumns().addAll(nameCol, ctCol, tatCol, wtCol);
        return table;
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
