import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class SubdomainFinder {
    private static final Map<String, String> DEFAULT_COMMANDS = new HashMap<>();
    
    static {
        DEFAULT_COMMANDS.put("subfinder", "subfinder -d %s -silent");
        DEFAULT_COMMANDS.put("assetfinder", "assetfinder --subs-only %s");
        DEFAULT_COMMANDS.put("amass", "amass enum -passive -d %s");
        DEFAULT_COMMANDS.put("findomain", "findomain -t %s -q");
        DEFAULT_COMMANDS.put("crt.sh", "curl -s 'https://crt.sh/?q=%%25.%s&output=json' | jq -r '.[].name_value' | sort -u");
        DEFAULT_COMMANDS.put("sublist3r", "sublist3r -d %s -o /dev/stdout");
    }
    
    public Set<String> findSubdomains(String domain, List<String> selectedTools, Scanner scanner) {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("Starting subdomain enumeration...");
        System.out.println("=".repeat(50));
        
        List<String> commands = configureCommands(scanner, selectedTools, domain);
        Set<String> allSubdomains = executeCommands(commands);
        
        if (!allSubdomains.isEmpty()) {
            displayEnumerationSummary(allSubdomains, selectedTools);
        } else {
            System.out.println("No subdomains found. Check your tools and commands.");
        }
        
        return allSubdomains;
    }
    
    public List<String> selectTools(Scanner scanner) {
        System.out.println("\nAvailable Subdomain Enumeration Tools:");
        System.out.println("=".repeat(45));
        
        List<String> availableTools = new ArrayList<>(DEFAULT_COMMANDS.keySet());
        for (int i = 0; i < availableTools.size(); i++) {
            System.out.printf("%d. %s\n", i + 1, availableTools.get(i));
        }
        
        System.out.println("\nSelection Options:");
        System.out.println("• Enter numbers separated by commas (e.g., 1,2,3)");
        System.out.println("• Enter 'all' to select all tools");
        System.out.println("• Enter 'recommended' for subfinder, assetfinder, amass");

        List<String> selectedTools = new ArrayList<>();
        boolean validSelection = false;

        while (!validSelection) {
            System.out.print("\nYour selection: ");
            String input = scanner.nextLine().trim().toLowerCase();

            if (input.equals("all")) {
                selectedTools = new ArrayList<>(availableTools);
                validSelection = true;
            } else if (input.equals("recommended")) {
                selectedTools = Arrays.asList("subfinder", "assetfinder", "amass");
                validSelection = true;
            } else {
                try {
                    String[] selections = input.split(",");
                    Set<String> uniqueTools = new HashSet<>();
                    
                    for (String sel : selections) {
                        int index = Integer.parseInt(sel.trim()) - 1;
                        if (index >= 0 && index < availableTools.size()) {
                            uniqueTools.add(availableTools.get(index));
                        } else {
                            throw new IndexOutOfBoundsException();
                        }
                    }
                    
                    selectedTools = new ArrayList<>(uniqueTools);
                    validSelection = true;
                } catch (Exception e) {
                    System.out.println("Invalid selection. Please try again.");
                }
            }
        }

        System.out.println("\nSelected tools: " + String.join(", ", selectedTools));
        return selectedTools;
    }
    
    private List<String> configureCommands(Scanner scanner, List<String> tools, String domain) {
        System.out.println("\nCommand Configuration:");
        System.out.println("=".repeat(40));
        
        List<String> commands = new ArrayList<>();
        
        for (String tool : tools) {
            String defaultCmd = String.format(DEFAULT_COMMANDS.get(tool), domain);
            System.out.println("\nTool: " + tool);
            System.out.println("Default command: " + defaultCmd);
            System.out.print("Use default? (y/n/custom): ");
            
            String choice = scanner.nextLine().trim().toLowerCase();
            
            switch (choice) {
                case "y":
                case "yes":
                case "":
                    commands.add(defaultCmd);
                    break;
                case "n":
                case "no":
                    System.out.println("Skipping " + tool);
                    break;
                case "custom":
                case "c":
                    System.out.print("Enter custom command for " + tool + ": ");
                    String customCmd = scanner.nextLine().trim();
                    if (!customCmd.isEmpty()) {
                        commands.add(customCmd);
                    }
                    break;
                default:
                    commands.add(defaultCmd);
                    break;
            }
        }
        
        return commands;
    }
    
    private Set<String> executeCommands(List<String> commands) {
        Set<String> allSubdomains = Collections.synchronizedSet(new HashSet<>());
        
        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i);
            System.out.printf("\n[%d/%d] Running: %s\n", i + 1, commands.size(), command);
            
            long startTime = System.currentTimeMillis();
            Set<String> results = runCommand(command);
            long endTime = System.currentTimeMillis();
            
            System.out.printf("Found %d subdomains in %.2fs\n", 
                            results.size(), (endTime - startTime) / 1000.0);
            
            allSubdomains.addAll(results);
        }
        
        return allSubdomains;
    }
    
    private Set<String> runCommand(String command) {
        Set<String> outputLines = new HashSet<>();
        try {
            ProcessBuilder builder = new ProcessBuilder("bash", "-c", command);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && isValidSubdomain(line)) {
                        outputLines.add(line.toLowerCase());
                    }
                }
            }

            if (!process.waitFor(300, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                System.out.println("Command timed out and was terminated");
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Error running command: " + e.getMessage());
        }
        return outputLines;
    }
    
    private boolean isValidSubdomain(String subdomain) {
        return subdomain.matches("^[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}$") &&
               !subdomain.startsWith(".") &&
               !subdomain.endsWith(".") &&
               subdomain.length() > 3;
    }
    
    private void displayEnumerationSummary(Set<String> subdomains, List<String> tools) {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("SUBDOMAIN ENUMERATION COMPLETE");
        System.out.println("=".repeat(50));
        System.out.println("Tools used: " + String.join(", ", tools));
        System.out.println("Total unique subdomains found: " + subdomains.size());
        
        if (subdomains.size() <= 15) {
            System.out.println("\nAll discovered subdomains:");
            subdomains.stream().sorted().forEach(s -> System.out.println("  • " + s));
        } else {
            System.out.println("\nSample subdomains (first 15):");
            subdomains.stream().sorted().limit(15)
                     .forEach(s -> System.out.println("  • " + s));
            System.out.println("  ... and " + (subdomains.size() - 15) + " more");
        }
    }
}