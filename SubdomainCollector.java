import java.io.*;
import java.util.*;

public class SubdomainCollector {

    public static void main(String[] args) {
        // Print the header
        printHeader();

        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Subdomain Collector ===");

        // Ask the user for the domain
        System.out.print("Enter the domain: ");
        String domain = scanner.nextLine().trim();

        // List of tools
        String[] tools = {"subfinder", "assetfinder", "amass"};

        // List to hold commands
        List<String> commands = new ArrayList<>();

        // Ask user to enter command for each tool
        for (String tool : tools) {
            System.out.print("Enter command for " + tool + " (use '" + domain + "' in command): ");
            String command = scanner.nextLine().trim();
            if (!command.isEmpty()) {
                commands.add(command);
            }
        }

        // Set to hold unique subdomains
        Set<String> subdomains = new HashSet<>();

        // Run each command and collect output
        for (String command : commands) {
            System.out.println("\nRunning: " + command);
            subdomains.addAll(runCommand(command));
        }

        // Save unique subdomains to a file
        String outputFile = domain + "_subdomains.txt";
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            for (String subdomain : subdomains) {
                writer.println(subdomain);
            }
            System.out.println("\nAll unique subdomains saved to: " + outputFile);
        } catch (FileNotFoundException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }

        scanner.close();
    }

    private static void printHeader() {
        System.out.println("===============================================================");
        System.out.println("                      SubMergeX - Subdomain Collector");
        System.out.println("===============================================================");
        System.out.println("Description:");
        System.out.println("SubMerge is a Linux-based Java tool that collects subdomains");
        System.out.println("from multiple sources (subfinder, assetfinder, amass, etc.)");
        System.out.println("using custom user commands. It merges all results, removes");
        System.out.println("duplicates, and saves them into a single output file.");
        System.out.println("\nFeatures:");
        System.out.println(" - Accepts custom commands for each tool");
        System.out.println(" - Runs tools sequentially");
        System.out.println(" - Removes duplicate entries");
        System.out.println(" - Saves all unique subdomains to a text file");
        System.out.println("\nAuthor: Rabia Ishtiaq");
        System.out.println("GitHub: https://github.com/rabbiya987/SubMergeX");
        System.out.println("Date: 2025-08-21");
        System.out.println("===============================================================\n");
    }

    private static Set<String> runCommand(String command) {
        Set<String> outputLines = new HashSet<>();
        try {
            // Linux shell execution
            ProcessBuilder builder = new ProcessBuilder();
            builder.command("bash", "-c", command);
            Process process = builder.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    outputLines.add(line.trim());
                }
            }

            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println("Error running command '" + command + "': " + e.getMessage());
        }
        return outputLines;
    }
}
