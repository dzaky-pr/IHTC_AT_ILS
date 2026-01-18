# IHTP Competition 2025

The problem set and more further about the problem. [Link](https://ihtc2024.github.io/)

<ul>
    <li><a href="https://github.com/jhoniananta">Jhoni</a></li>
    <li><a href="https://github.com/bryanmichaelk">Bryan</a></li>
    <li><a href="https://github.com/dzaky-pr">Dzaky</a></li>
</ul>

## Getting started with the Project

To get local copy up and running follow these simple example steps.

### Prerequisites

You need to download and install some tools to run the program.

- #### Java Development Kits

  visit java official installation based on your machine: [Download](https://www.oracle.com/id/java/technologies/downloads/)

- #### Visual Studio Code
  visit VSCode official installation based on your machine: [Download](https://code.visualstudio.com/download)

#### Setup

_After downloading all prequities, you can follow this setup steps:_

1. Clone the repo

   ```sh
   git clone https://github.com/bryanmichaelk/TimeTabling_Optimization_NPA_PRA.git
   ```

2. Go to clone directory

   ```sh
   cd Timetabling_Optimization_NPA_PRA
   ```

3. Compile the solution program
   Windows
   ```sh
   javac -cp .;json-20250107.jar IHTP_Solution.java
   ```
   Macos
   ```sh
   javac -cp .:json-20250107.jar IHTP_Solution.java
   ```
4. Generate the solution
   Windows
   ```sh
   java -cp .;json-20250107.jar IHTP_Solution {pathTest.json} {max_hour} violation_log{numberTest}.csv solution{numberTest}.json
   ```
   Macos
   ```sh
   java -cp .:json-20250107.jar IHTP_Solution {pathTest.json} {max_hour} violation_log{numberTest}.csv solution{numberTest}.json
   ```
5. Compile the validator program
   Windows

   ```sh
   javac -cp .;json-20250107.jar IHTP_Validator.java
   ```

   Macos

   ```sh
   javac -cp .:json-20250107.jar IHTP_Validator.java
   ```

6. Test the solution
   Windows
   ```sh
   java -cp .;json-20250107.jar IHTP_Validator {pathTest.json} solution.json
   ```
   Macos
   ```sh
   java -cp .:json-20250107.jar IHTP_Validator {pathTest.json} solution.json
   ```

### Test With UI

1. Compile all the Program:
   Windows
   ```sh
   javac -cp .;json-20250107.jar IHTP_Solution.java
   javac -cp .;json-20250107.jar IHTP_Validator.java
   ```
   Macos
   ```sh
   javac -cp .:json-20250107.jar IHTP_Solution.java
   javac -cp .:json-20250107.jar IHTP_Validator.java
   ```
2. Comple UI:
   ```sh
   javac IHTP_Launcher.java
   ```
3. Run The UI:
   ```sh
   java IHTP_Launcher
   ```
