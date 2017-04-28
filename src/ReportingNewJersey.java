/**
 * Created by nick on 4/25/2017.
 */

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.*;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class ReportingNewJersey {


    final String USER_AGENT = "\"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_4) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/57.0.2987.133 Safari/537.36";

    public static void main(String args[]) {
        if (args.length != 3) {
            System.out.println("The New Jersey Report requires:  filename, username, password");
            System.exit(1);
        }

        new ReportingNewJersey(args);
    }

    public ReportingNewJersey(String args[]) {

        String targetURL = "https://njrece.psiexams.com/account/login/Login";

        String filename = args[0];
        String username = args[1];
        String password = args[2];

        //Read CSV file
        List<String[]> students = getDataFromCSV(filename);
        ArrayList<String> list = null;

        //Login to site
        HashMap<String, String> cookies = login(targetURL, username, password);

        //Process student record
        for ( int row = 1; row <= students.size()-1; row++) {
            //Check to see if they have already been submitted
            if(students.get(row).length < 9 || students.get(row)[8] == null) {
                //Submit record
                list = new ArrayList<String>(Arrays.asList(students.get(row)));
                list.add(submitData(cookies, students.get(row)));
                students.set(row, list.toArray(new String[list.size()]));
                writeDatatoCSV(filename, students);
            }
        }

        //Completed processing, now write error file
        ArrayList<String[]> errorList = new ArrayList<String[]>();
        errorList.add(students.get(0));
        for ( int row = 1; row <= students.size()-1; row++) {
            //Check to see if they have do not have Completed in the status
            if(!students.get(row)[8].contains("Completed")) {
                errorList.add(students.get(row));
            }
        }
        if (errorList.size() > 1)
        {
            String filename2 = String.valueOf(new StringBuilder(filename).insert(filename.lastIndexOf("\\")+1,"Failed-"));
            writeDatatoCSV(filename2,errorList);
        }

        //Completed Run.
        System.out.println("New Jersey completed successfully.");
    }

    private HashMap<String, String> login(String targetURL, String username, String password) {
        try {
            HashMap<String, String> cookies = new HashMap<>();
            HashMap<String, String> formData = new HashMap<>();

            //formData.put("utf8", "e2 9c 93");
            formData.put("MemberIdentifier", username);
            formData.put("Password", password);

            Response loginForm = Jsoup.connect(targetURL)
                    .method(Method.GET)
                    .userAgent(USER_AGENT)
                    .execute();

            cookies.putAll(loginForm.cookies());

            Response homePage = Jsoup.connect(targetURL)
                    .data(formData)
                    .cookies(cookies)
                    .userAgent(USER_AGENT)
                    .method(Method.POST)
                    .execute();

            //System.out.println("RES " + homePage.parse().html());

            return cookies;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private List<String[]> getDataFromCSV(String filename) {

        try {
            //csv file containing data
            //create BufferedReader to read csv file
            BufferedReader br = new BufferedReader(new FileReader(filename));
            CsvParserSettings settings = new CsvParserSettings();

            settings.getFormat().setLineSeparator("\n");

            // creates a CSV parser
            CsvParser parser = new CsvParser(settings);

            // parses all rows in one go.
            List<String[]> allRows = parser.parseAll(br);
            System.out.println("RES ");
            return allRows;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean writeDatatoCSV(String filename, List<String[]> data) {

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
            CsvWriter writer = new CsvWriter( bw, new CsvWriterSettings());

            // Here we just tell the writer to write everything and close the given output Writer instance.
            writer.writeStringRowsAndClose(data);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private String submitData(HashMap<String, String> cookies, String[] student) {
        final String ATTENDANCE_URL = "https://njrece.psiexams.com/Provider/Attendance/ProcessEnteredAttendance";
        int year = Calendar.getInstance().get(Calendar.YEAR);
        if ((year & 1) == 0) year++;

        HashMap<String, String> formData = new HashMap<>();
//		formData.put("utf8", "e2 9c 93");
        formData.put("OrganizationId", "168187");
        formData.put("ActivityId", student[3]);
        formData.put("PSI_InstructorReferenceNumber", student[6]);
        formData.put("GrantedUnits", student[4]);
        formData.put("CompletionDate", student[7]);
        formData.put("CycleEndYear", student[5] == null ? String.valueOf(year) : student[5]);
        formData.put("UniqueId", student[0]);
        formData.put("FirstName", student[1]);
        formData.put("LastName", student[2]);

        try {
            Response attendanceForm = Jsoup.connect(ATTENDANCE_URL)
                    .data(formData)
                    .cookies(cookies)
                    .userAgent(USER_AGENT)
                    .method(Method.POST)
                    .execute();

            Document temp = attendanceForm.parse();
            String status = null;
            String success = temp.select(".informational-message").outerHtml();
            String error = temp.select(".errors li").outerHtml();
            if (success.contains("Record was successfully processed")) {
                status = "Completed";
            } else if (error.contains("because it has already been added")) {
                status = "Completed - Student already processed";
            } else {
                status = error;
            }
            return status;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
