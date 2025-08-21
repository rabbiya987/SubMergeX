# SubMergeX - Subdomain Collector

**SubMerge** is a Linux-based Java tool that collects subdomains from multiple sources, merges the results, removes duplicates, and saves them to a single file. It allows you to run **custom commands** for subdomain enumeration tools like `subfinder`, `assetfinder`, and `amass`.

---

## Features

- Accepts custom commands for each tool
- Runs tools sequentially
- Removes duplicate subdomains automatically
- Saves all unique subdomains to `<domain>_subdomains.txt`

---

## Requirements

- Java 17 or higher
- Linux environment
- Installed subdomain enumeration tools (`subfinder`, `assetfinder`, `amass`, etc.)

---

## Installation

1. Clone the repository:

```bash
git clone https://github.com/rabbiya987/SubMergeX.git
cd SubMerge
````

2. Compile the Java program:

```bash
javac app.java
```

3. Run the tool:

```bash
java app
```

---

## Usage Example

```text
Enter the domain: example.com
Enter command for subfinder (use 'example.com' in command): subfinder -d example.com -silent
Enter command for assetfinder (use 'example.com' in command): assetfinder example.com
Enter command for amass (use 'example.com' in command): amass enum -d example.com
```

* Output: `example.com_subdomains.txt` containing all unique subdomains.

---
