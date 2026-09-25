package com.example.daily_learn.domain;

import com.example.daily_learn.domain.ai.DailyTopicGroup;
import com.example.daily_learn.domain.ai.SyllabusAnalysisResult;
import com.example.daily_learn.domain.ai.SyllabusAnalyzer;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SyllabusAnalyzerTest {

    private SyllabusAnalyzer analyzer;

    @Before
    public void setUp() {
        analyzer = new SyllabusAnalyzer(null);
    }

    private static final String TEST_OOP_SYLLABUS =
            "Course Description\n" +
            "25SS212 / OBJECT ORIENTED PROGRAMMING\n" +
            "Semester II Year December 2025 – May 2026\n" +
            "Name of the Faculty : Dr. S. Manjula Gandhi\n" +
            "E-mail: manjulagandhi@cit.edu.in/9443128501\n" +
            "LinkedIn: https://www.linkedin.com/in/dr-manjula-gandhi-s-3a4b6a110/\n" +
            "Class Schedule\n" +
            "Lecture: Days: Monday, Tuesday, Thursday Timings: 9:25-10:20, 11:30-12:25, 10:35-11:30\n" +
            "Hours for meeting students: Thursday 3:50 – 4:45 p.m.\n" +
            "i)Course Objective\n" +
            "1. For a given scenario compare and criticize alternate implementations using functional programming and object oriented programming in view of privacy.\n" +
            "2. Create persistent objects using stream class hierarchy incorporating exceptional handling.\n" +
            "3. Compare and contrast function overloading and default argument function for appropriate usage in an object oriented system.\n" +
            "4. For a given scenario, construct an object oriented system using appropriate type of inheritance for realizing reusability.\n" +
            "5. Construct and compare object oriented systems using static binding, dynamic binding and template classes for realizing polymorphism.\n" +
            "ii) Once the student has successfully completed this course, he/she must be able to answer the following questions or perform/demonstrate the following:\n" +
            "1. Understand the features of C++\n" +
            "2. Will be able to write classes and work with objects\n" +
            "3. Will be able to define and bind property and methods together\n" +
            "4. Differentiate function overloading and passing default values to functions\n" +
            "5. Work with constructors and destructors\n" +
            "6. Dynamic allocation and deallocation of memory\n" +
            "7. Demonstrate the working of File I/O streams\n" +
            "8. Will know how to overload unary, binary operators\n" +
            "9. Understand the various ways to inherit a class using access specifier\n" +
            "10. Implement types of inheritance\n" +
            "11. Implement Dynamic polymorphism\n" +
            "12. Differentiate static and dynamic polymorphism\n" +
            "13. Working with Friend functions and Friend classes\n" +
            "14. Working with Function templates, Class templates\n" +
            "15. Exception Handling\n" +
            "iii) Topic/Unit/Chapter Layout\n" +
            "Topic Lecture Hours\n" +
            "PRINCIPLES OF OBJECT ORIENTED PROGRAMMING\n" +
            "Software crisis, Software Evolution - Procedure Oriented Programming - Object Oriented\n" +
            "Programming Paradigm - Basic Concepts and Benefits of OOP - Object Oriented\n" +
            "Programming Language - Application of OOP - Structure of C++ 3\n" +
            "Tokens, Expressions and Control Structures - Operators in C++ - Classes and Objects -\n" +
            "Member functions – Nesting of Member functions - Private member functions - Memory\n" +
            "allocation for Objects - Static data members - Static Member Functions - Arrays of\n" +
            "Objects - Objects as Function Arguments – Returning objects-Manipulators. 6\n" +
            "FUNCTIONS IN C++\n" +
            "Function Prototyping - Call by Reference - Return by reference - Inline functions -\n" +
            "Default, Const Arguments - Function Overloading - Friend Functions - Friend Class -\n" +
            "Const Member functions - Pointers to Members – Passing default values to functions 5\n" +
            "CONSTRUCTORS: Parameterized Constructors - Multiple Constructors in a Class -\n" +
            "Constructors with Default Arguments - Dynamic Initialization of Objects - Copy\n" +
            "Constructors – Destructors 4\n" +
            "STREAMS AND FILES\n" +
            "Streams classes - Stream Errors - Disk File I/O with streams - File pointers - Error\n" +
            "handling in file I/O with member function - Command line arguments. 6\n" +
            "INHERITANCE AND POLYMORPHISM\n" +
            "Inheritance: Introduction and benefits - Access Specifier - Base and Derived Class\n" +
            "Constructors - Types of Inheritance - Down casting and up casting- Virtual base class. 5\n" +
            "Polymorphism: Defining Operator Overloading - Overloading Unary Operators -\n" +
            "Overloading Binary Operators - Overloading Binary Operators using Friends -\n" +
            "Manipulation of Strings using Operators - Rules of Operator Overloading - Type\n" +
            "Conversion. 5\n" +
            "Function Overriding - Virtual functions – Pure virtual function. 3\n" +
            "TEMPLATES AND EXCEPTIONS\n" +
            "Function templates, Class templates. Exceptions-Grouping of exceptions - Catching\n" +
            "exceptions – Exceptions that are not errors – Uncaught Exceptions – Standard exceptions. 8\n" +
            "iv) Reference books\n" +
            "1. Bjarne Stroustrup, “The C++ Programming Language”, 3rd edition, Pearson Education, 2000.\n" +
            "2. Tony Gaddis, \"Starting Out with C++ from Control Structures to Objects \", 8th edition, Pearson Education, 2014.\n" +
            "3. Ira Pohl, “Object Oriented Programming Using C++”, 2nd Edition, Pearson Education, 2006.\n" +
            "4. Herbert Schildt, “The Complete Reference C++”, 4th edition, Tata McGraw Hill, 2003.\n" +
            "5. Stanley B Lippman, Jove Lajoie, and Barbara Moo “C++ Primer”, 5th Edition, Addison Wesley, 2012.\n" +
            "v) Evaluation Scheme\n" +
            "Evaluation Criteria Conducted Marks Converted Marks\n" +
            "Sessional Exam 1 60 100\n" +
            "Assignment –Set 1 40\n" +
            "Sessional Exam 2 60 100\n" +
            "Assignment – Set 2 40\n" +
            "Internal Marks 40\n" +
            "External Exam 100 60\n" +
            "Total 100\n";

    @Test
    public void testOopSyllabusAnalysis() {
        int targetDays = 20;
        SyllabusAnalysisResult result = analyzer.processRawText(TEST_OOP_SYLLABUS, targetDays);

        assertNotNull("Result should not be null", result);

        // 1. Verify Course Metadata
        assertEquals("Course code must match", "25SS212", result.courseCode);
        assertEquals("Course name must match", "Object Oriented Programming", result.courseName);
        assertEquals("Total days must be 20", 20, result.totalDays);
        assertEquals("Days list size must be 20", 20, result.days.size());

        // 2. Verify Non-Learning Content is NOT in the topics
        for (DailyTopicGroup day : result.days) {
            assertNotNull(day.title);
            assertFalse("Day title should not be empty", day.title.isEmpty());
            assertFalse("Day topics should not be empty", day.topics.isEmpty());

            for (String topic : day.topics) {
                assertFalse("Faculty name must not be a topic: " + topic, topic.toLowerCase().contains("manjula"));
                assertFalse("Email must not be a topic: " + topic, topic.toLowerCase().contains("email"));
                assertFalse("LinkedIn must not be a topic: " + topic, topic.toLowerCase().contains("linkedin"));
                assertFalse("Class schedule must not be a topic: " + topic, topic.toLowerCase().contains("class schedule"));
                assertFalse("Timetable timings must not be a topic: " + topic, topic.toLowerCase().contains("timings"));
                assertFalse("Reference books must not be a topic: " + topic, topic.toLowerCase().contains("stroustrup") || topic.toLowerCase().contains("reference books"));
                assertFalse("Evaluation scheme must not be a topic: " + topic, topic.toLowerCase().contains("sessional exam") || topic.toLowerCase().contains("evaluation scheme"));
            }
        }

        // 3. Verify Learning Content Coverage
        boolean foundSoftwareCrisis = false;
        boolean foundInheritance = false;
        boolean foundTemplates = false;
        boolean foundExceptions = false;
        boolean foundStreams = false;

        for (DailyTopicGroup day : result.days) {
            for (String topic : day.topics) {
                String lower = topic.toLowerCase();
                if (lower.contains("software crisis")) foundSoftwareCrisis = true;
                if (lower.contains("inheritance")) foundInheritance = true;
                if (lower.contains("template")) foundTemplates = true;
                if (lower.contains("exception")) foundExceptions = true;
                if (lower.contains("stream")) foundStreams = true;
            }
        }

        assertTrue("Must extract Software Crisis", foundSoftwareCrisis);
        assertTrue("Must extract Inheritance", foundInheritance);
        assertTrue("Must extract Templates", foundTemplates);
        assertTrue("Must extract Exceptions", foundExceptions);
        assertTrue("Must extract Streams", foundStreams);

        // 4. Verify JSON Serialization
        String json = result.toJsonString();
        assertNotNull(json);
        assertTrue("JSON should contain courseName", json.contains("Object Oriented Programming"));
        assertTrue("JSON should contain days", json.contains("\"days\":"));
    }

    @Test
    public void testDataStructuresSyllabusAnalysis() {
        String dsSyllabus = "CS8391 / DATA STRUCTURES\n" +
                "UNIT I LINEAR DATA STRUCTURES - LIST\n" +
                "Abstract Data Types (ADTs) - List ADT - array-based implementation - linked list implementation - singly linked lists- circularly linked lists- doubly-linked lists - applications of lists -Polynomial Manipulation - All operations (Insertion, Deletion, Merge, Traversal).\n" +
                "UNIT II LINEAR DATA STRUCTURES - STACKS, QUEUES\n" +
                "Stack ADT - Operations - Applications - Evaluating arithmetic expressions- Conversion of Infix to postfix expression - Queue ADT - Operations - Circular Queue - Priority Queue - deQueue - applications of queues.\n" +
                "UNIT III NON LINEAR DATA STRUCTURES - TREES\n" +
                "Tree ADT - tree traversals - Binary Tree ADT - expression trees - binary search tree ADT -Threaded Binary Trees- AVL Trees - B-Tree - B+ Tree - Heap - Applications of heap.\n" +
                "UNIT IV NON LINEAR DATA STRUCTURES - GRAPHS\n" +
                "Definition - Representation of Graph - Types of graph - Breadth-first traversal - Depth-first traversal - Topological Sort - Bi-connectivity - Cut vertex - Euler circuits - Applications of graphs.\n" +
                "UNIT V SEARCHING, SORTING AND HASHING TECHNIQUES\n" +
                "Searching- Linear Search - Binary Search. Sorting - Bubble sort - Selection sort - Insertion sort - Shell sort - Radix sort. Hashing- Hash Functions – Separate Chaining – Open Addressing – Rehashing – Extendible Hashing.\n";

        SyllabusAnalysisResult result = analyzer.processRawText(dsSyllabus, 15);

        assertEquals("CS8391", result.courseCode);
        assertEquals("Data Structures", result.courseName);
        assertEquals(15, result.totalDays);
        assertEquals(15, result.days.size());

        boolean hasLinkedList = false;
        boolean hasBst = false;
        boolean hasGraph = false;

        for (DailyTopicGroup day : result.days) {
            for (String t : day.topics) {
                if (t.toLowerCase().contains("linked list")) hasLinkedList = true;
                if (t.toLowerCase().contains("binary search tree") || t.toLowerCase().contains("tree")) hasBst = true;
                if (t.toLowerCase().contains("graph") || t.toLowerCase().contains("traversal")) hasGraph = true;
            }
        }

        assertTrue(hasLinkedList);
        assertTrue(hasBst);
        assertTrue(hasGraph);
    }

    @Test
    public void testFundamentalsOfWebTechnologySyllabus() {
        String webTechSyllabus = "20MSS23 FUNDAMENTALS OF WEB TECHNOLOGY\n" +
                "Contact Hours\n" +
                "L T P C\n" +
                "3 0 0 3\n" +
                "PRE-REQUISITES\n" +
                "Consent of the Instructor\n" +
                "ASSESSMENT : THEORY\n" +
                "COURSE OUTCOME\n" +
                "• Understand the purpose of Web Servers and its functionalities\n" +
                "• Create responsive user interface web pages using HTML5, CSS and BOOTSTRAP\n" +
                "• Design and validate richly interactive web pages using AJAX, JSON, jQuery\n" +
                "• Employ Java Script for client side programming that uses a regular expression to validate form entry and processing and event handling\n" +
                "• Establish PHP server side programming skills to work with form data, regular expressions, exception handling, validate data, cookies, sessions and to build interactive, data-driven sites\n" +
                "INTRODUCTION\n" +
                "History of Internet and World Wide Web – Web basics – Multitier Application architecture – Client side scripting versus Server side scripting – W3C (4)\n" +
                "HTML5\n" +
                "Introduction to HTML5: Basic Tags – Headings – Links – Images – Special characters – Horizontal rules – Lists - Tables - Frames – Forms- Meta element - New form input types – Data list Elements and Auto complete Attribute – Page structure elements, HTML Graphics: Canvas (8)\n" +
                "CSS AND BOOTSRAP\n" +
                "CSS Introduction - Inline Styles - Embedded Style Sheets - Conflicting Styles - Linking External Style Sheets - Positioning Elements: Absolute Positioning, Relative Positioning - Backgrounds - Element Dimensions - Box Model and Text Flow, Cascading Style Sheet (CSS3): Introduction - Text Shadows - Rounded Corners - Color - Box Shadows - Linear Gradients - Radial Gradients - Text Stroke - Multiple Background Images - Reflections - Image Borders - Animation - Selectors - Transitions and Transformations - Downloading Web Fonts - Flexible Box Layout Module - Multicolumn Layout - Media Queries, Bootstrap: Introduction to Bootstrap – grid – button – cards – navbar – dropdown – carousel – forms – alerts (8)\n" +
                "JAVA SCRIPT\n" +
                "Introduction to scripting - Control Statements - Functions - Arrays - Objects – Collections – Event handling, Introduction to JSON- JSON syntax – JSON parsing – JSON serialization, Introduction to jQuery – validations - events, Introduction to AJAX: Traditional Web Applications, Ajax Applications, Rich Internet Applications (RIAs) with Ajax, History of Ajax, Ajax Example Using the XMLHttpRequest Object, Using XML and the DOM, Creating a Full-Scale Ajax-Enabled Application using JSON (15)\n" +
                "PHP\n" +
                "Introduction – simple PHP program - converting between data types – Arithmetic operators – Arrays – String comparisons – String processing with regular expressions – Form processing and Business logic – Reading from a database – Cookies – Dynamic content (10)\n" +
                "TOTAL : 45 Hours\n" +
                "TEXT BOOKS\n" +
                "1. Paul Deitel, Harvey Deitel, Abbey Deitel, \"Internet and World Wide Web How To Program\", fifth edition, Pearson Education, 2011.\n" +
                "REFERENCE BOOKS\n" +
                "1. Nicholas C. Zahas, \"Professional - Java Script for Web Developers\", Third Edition, 2012.\n" +
                "WEB REFERENCES\n" +
                "1. https://www.w3schools.com/html/\n";

        SyllabusAnalysisResult result = analyzer.processRawText(webTechSyllabus, 20);

        assertEquals("20MSS23", result.courseCode);
        assertEquals("Fundamentals Of Web Technology", result.courseName);
        assertEquals(20, result.totalDays);

        // Verify NO preamble/outcomes in any topic
        for (DailyTopicGroup day : result.days) {
            for (String t : day.topics) {
                String lower = t.toLowerCase(java.util.Locale.ROOT);
                assertFalse("Must not contain consent of instructor: " + t, lower.contains("consent of the instructor"));
                assertFalse("Must not contain assessment: " + t, lower.contains("assessment"));
                assertFalse("Must not contain driven sites outcome: " + t, lower.equals("driven sites"));
                assertFalse("Must not contain course outcome prose: " + t, lower.contains("purpose of web servers"));
                assertFalse("Must not contain hours: " + t, t.matches(".*\\(\\d+\\).*"));
                assertFalse("Must not contain textbook author: " + t, lower.contains("paul deitel") || lower.contains("harvey deitel"));
            }
        }
    }
}
