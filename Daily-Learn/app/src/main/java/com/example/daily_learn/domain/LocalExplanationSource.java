package com.example.daily_learn.domain;

/**
 * Local implementation of ExplanationSource.
 * Returns seeded static content so the app is fully demoable without AI/network.
 * Replace or extend with an AI-backed implementation for Phase 5.
 */
public class LocalExplanationSource implements ExplanationSource {

    @Override
    public String getExplanation(String topicName) {
        return getExplanation(topicName, null);
    }

    @Override
    public String getExplanation(String topicName, String subjectName) {
        if (topicName == null) return getDefaultExplanation();
        String lower = topicName.toLowerCase();

        if (isWebTech(topicName, subjectName)) {
            if (lower.contains("html")) {
                return "HTML (HyperText Markup Language) is the foundational language of the World Wide Web. " +
                       "It uses semantic tags and elements to define the structure, content, and accessibility " +
                       "of web documents, including text, media, forms, and interactive components.";
            }
            if (lower.contains("css")) {
                return "CSS (Cascading Style Sheets) defines the presentation, styling, and responsive layout " +
                       "of web documents. Key concepts include the CSS box model, flexbox, grid, typography, " +
                       "and media queries for adaptive multi-device presentation.";
            }
            if (lower.contains("javascript") || lower.contains("script") || lower.contains("jquery") || lower.contains("json")) {
                return "JavaScript is the primary programming language of the modern web. It provides dynamic " +
                       "client-side interactivity, DOM manipulation, asynchronous communication (AJAX/Fetch), " +
                       "and event-driven programming across modern browsers.";
            }
            if (lower.contains("multitier") || lower.contains("architecture")) {
                return "Multitier application architecture organizes web systems into separate physical and " +
                       "logical tiers: presentation tier (browser client), application tier (business logic), " +
                       "and data tier (database). This separation improves scalability, security, and maintainability.";
            }
            if (lower.contains("client") || lower.contains("server") || lower.contains("http") || lower.contains("internet")) {
                return "Web communication operates on the client-server architecture over HTTP/HTTPS protocols. " +
                       "Browsers act as clients sending requests, while web servers process these requests and " +
                       "deliver web resources, handling sessions, cookies, and asynchronous data transfer.";
            }
            return "Web Technology encompasses the standards, protocols, and markup languages used to build " +
                   "and deploy modern web applications. Focus on understanding client-server communication, " +
                   "responsive layouts, and dynamic content rendering.";
        }

        if (lower.contains("linked list") && lower.contains("singly")) {
            return "A Singly Linked List is a linear data structure where each element (node) contains " +
                   "data and a pointer to the next node. Unlike arrays, nodes are not stored in " +
                   "contiguous memory locations. The last node points to NULL, marking the end of the list. " +
                   "This structure allows efficient insertions and deletions without reallocation.";
        }
        if (lower.contains("binary search tree") || lower.contains("bst")) {
            return "A Binary Search Tree (BST) is a binary tree where each node has a key greater than " +
                   "all keys in its left subtree and less than all keys in its right subtree. This property " +
                   "enables O(log n) average-case search, insertion, and deletion operations. BSTs are the " +
                   "foundation for many advanced data structures like AVL trees and Red-Black trees.";
        }
        if (lower.contains("stack")) {
            return "A Stack is a Last-In First-Out (LIFO) data structure. Elements are added and removed " +
                   "from the same end called the 'top'. The two primary operations are PUSH (add element) " +
                   "and POP (remove element). Stacks are used in function call management, expression " +
                   "evaluation, backtracking algorithms, and undo operations.";
        }
        if (lower.contains("queue")) {
            return "A Queue is a First-In First-Out (FIFO) data structure. Elements are added at the 'rear' " +
                   "and removed from the 'front'. Key operations: ENQUEUE (add) and DEQUEUE (remove). " +
                   "Queues model real-world waiting lines and are used in BFS graph traversal, " +
                   "CPU scheduling, and printer spooling.";
        }
        if (lower.contains("process") || lower.contains("cpu scheduling") || lower.contains("scheduling")) {
            return "CPU Scheduling is the mechanism by which the OS decides which process gets CPU time. " +
                   "Common algorithms include FCFS (First-Come First-Served), SJF (Shortest Job First), " +
                   "Round Robin, and Priority Scheduling. Each balances throughput, response time, and " +
                   "CPU utilization differently.";
        }
        if (lower.contains("osi")) {
            return "The OSI (Open Systems Interconnection) model is a conceptual framework with 7 layers: " +
                   "Physical, Data Link, Network, Transport, Session, Presentation, and Application. " +
                   "Each layer has specific responsibilities and communicates with adjacent layers via " +
                   "well-defined interfaces, enabling interoperability between diverse network technologies.";
        }
        if (lower.contains("paging") || lower.contains("virtual memory")) {
            return "Virtual Memory is a memory management technique that gives each process the illusion " +
                   "of a large, contiguous address space. Paging divides memory into fixed-size frames " +
                   "and process address space into pages. A page table maps virtual pages to physical frames. " +
                   "When a page is not in RAM, a page fault occurs and the OS loads it from disk.";
        }
        if (lower.contains("big-o") || lower.contains("algorithm analysis")) {
            return "Algorithm Analysis evaluates efficiency using Big-O notation, which describes the " +
                   "upper bound of time/space growth as input size n grows. O(1) is constant, O(log n) " +
                   "is logarithmic, O(n) is linear, O(n log n) is linearithmic, and O(n²) is quadratic. " +
                   "Always analyze both time complexity and space complexity.";
        }

        return getDefaultExplanation();
    }

    @Override
    public String getKeyConcepts(String topicName) {
        return getKeyConcepts(topicName, null);
    }

    @Override
    public String getKeyConcepts(String topicName, String subjectName) {
        if (topicName == null) return "Core Concepts • Architecture • Applications • Best Practices";
        String lower = topicName.toLowerCase();

        if (isWebTech(topicName, subjectName)) {
            if (lower.contains("html")) {
                return "Semantic Tags • DOM Tree • Form Elements • HTML5 Audio/Video • Canvas • Accessibility (ARIA)";
            }
            if (lower.contains("css")) {
                return "Box Model • Flexbox • CSS Grid • Media Queries • Selectors & Specificity • Transitions";
            }
            if (lower.contains("javascript") || lower.contains("script") || lower.contains("jquery") || lower.contains("json")) {
                return "DOM Manipulation • Event Listeners • Asynchronous JS • Fetch/AJAX • Promises • Scope & Closures";
            }
            if (lower.contains("multitier") || lower.contains("architecture")) {
                return "Presentation Tier • Application Tier • Data Tier • REST APIs • Scalability • Modular Separation";
            }
            return "Client-Server Architecture • Request-Response Cycle • DOM Hierarchy • Responsive Layouts • Web Standards • State Management";
        }

        if (lower.contains("linked list"))
            return "Node • Head Pointer • Next Pointer • Traversal • Insertion • Deletion • NULL terminator";
        if (lower.contains("stack"))
            return "LIFO • Push • Pop • Peek/Top • Overflow • Underflow • Stack Pointer";
        if (lower.contains("queue"))
            return "FIFO • Enqueue • Dequeue • Front • Rear • Circular Queue • Priority Queue";
        if (lower.contains("tree") || lower.contains("bst"))
            return "Root • Node • Leaf • Height • Depth • Inorder • Preorder • Postorder • Balance Factor";
        if (lower.contains("scheduling"))
            return "Context Switch • Preemptive • Non-Preemptive • Burst Time • Waiting Time • Turnaround Time • Throughput";
        if (lower.contains("osi"))
            return "Physical Layer • Data Link • Network • Transport • Session • Presentation • Application • Protocol Stack";
        if (lower.contains("paging"))
            return "Page • Frame • Page Table • TLB • Page Fault • Demand Paging • Thrashing • Working Set";

        if (isDsa(topicName, subjectName)) {
            return "Data Representation • Traversal & Search • Space Complexity • Time Complexity • Invariants • Edge Cases";
        }

        return "Core Principles • Architecture & Flow • Key Properties • Best Practices • Practical Applications";
    }

    @Override
    public String getExample(String topicName) {
        return getExample(topicName, null);
    }

    @Override
    public String getExample(String topicName, String subjectName) {
        if (topicName == null) return "Example: Apply the concept to a small practical sample to verify understanding.";
        String lower = topicName.toLowerCase();

        if (isWebTech(topicName, subjectName)) {
            if (lower.contains("html")) {
                return "HTML Example:\n" +
                       "<article class=\"card\">\n" +
                       "  <h2>Web Architecture</h2>\n" +
                       "  <p>Learn client-server fundamentals & semantic markup.</p>\n" +
                       "  <button id=\"learnBtn\">Start</button>\n" +
                       "</article>";
            }
            if (lower.contains("css")) {
                return "CSS Flexbox Example:\n" +
                       ".navbar {\n" +
                       "  display: flex;\n" +
                       "  justify-content: space-between;\n" +
                       "  align-items: center;\n" +
                       "  padding: 12px 24px;\n" +
                       "}";
            }
            if (lower.contains("javascript") || lower.contains("script") || lower.contains("jquery") || lower.contains("json")) {
                return "JavaScript DOM Example:\n" +
                       "document.getElementById('learnBtn').addEventListener('click', () => {\n" +
                       "  console.log('Button clicked! Updating UI...');\n" +
                       "});";
            }
            return "Example: Browser dispatches HTTP GET /index.html → Web server processes request and returns HTML/CSS payload → Browser parses DOM tree and renders page.";
        }

        if (lower.contains("linked list"))
            return "Linked List: 10 → 20 → 30 → NULL\n" +
                   "Insert 15 after 10: 10 → 15 → 20 → 30 → NULL\n" +
                   "Delete 20: 10 → 15 → 30 → NULL";
        if (lower.contains("stack"))
            return "Stack operations:\nPUSH(5) → [5]\nPUSH(10) → [5,10]\n" +
                   "PUSH(15) → [5,10,15]\nPOP() → returns 15, stack: [5,10]";
        if (lower.contains("queue"))
            return "Queue operations:\nENQUEUE(A) → [A]\nENQUEUE(B) → [A,B]\n" +
                   "ENQUEUE(C) → [A,B,C]\nDEQUEUE() → returns A, queue: [B,C]";
        if (lower.contains("bst"))
            return "BST Insert sequence 5,3,7,1,4:\n" +
                   "       5\n      / \\\n     3   7\n    / \\\n   1   4\n" +
                   "Search for 4: Root(5)→Left(3)→Right(4) ✓ Found in 3 steps";
        if (lower.contains("big-o"))
            return "Array of n=1000 elements:\n" +
                   "O(1)   → access arr[500]    → 1 operation\n" +
                   "O(n)   → linear search       → up to 1000 operations\n" +
                   "O(log n) → binary search     → up to 10 operations\n" +
                   "O(n²)  → bubble sort         → up to 1,000,000 operations";

        return "Example: Break down the concept into input, processing, and output phases to systematically verify your understanding.";
    }

    @Override
    public String getImportantPoints(String topicName) {
        return getImportantPoints(topicName, null);
    }

    @Override
    public String getImportantPoints(String topicName, String subjectName) {
        if (isWebTech(topicName, subjectName)) {
            return "• Understand client-side vs. server-side execution roles\n" +
                   "• Ensure cross-browser compatibility and mobile responsiveness\n" +
                   "• Adhere to semantic HTML structure and web standards\n" +
                   "• Validate user inputs and handle asynchronous network data cleanly\n" +
                   "• Inspect elements, console, and network traffic using Developer Tools";
        }

        if (isDsa(topicName, subjectName)) {
            return "• Understand the core definition before memorizing operations\n" +
                   "• Know the time complexity (best, average, worst case)\n" +
                   "• Practice at least 3 coding problems on this topic\n" +
                   "• Compare with similar data structures/concepts\n" +
                   "• Write out the algorithm by hand once before coding it";
        }

        if (isDbms(topicName, subjectName)) {
            return "• Understand schema design and entity relationships\n" +
                   "• Follow normalization rules (1NF to BCNF) to prevent anomalies\n" +
                   "• Maintain ACID guarantees for transactional integrity\n" +
                   "• Optimize queries using proper indexes and execution plans";
        }

        if (isOs(topicName, subjectName)) {
            return "• Differentiate between user mode and kernel mode execution\n" +
                   "• Analyze concurrency issues, race conditions, and deadlocks\n" +
                   "• Monitor virtual memory paging and cache efficiency\n" +
                   "• Understand the process and thread lifecycle";
        }

        return "• Understand the fundamental concept before moving to advanced details\n" +
               "• Identify how this topic integrates with previous modules\n" +
               "• Write clean, maintainable, and well-documented implementations\n" +
               "• Test with edge cases and unexpected inputs\n" +
               "• Review common interview and exam questions on this topic";
    }

    @Override
    public String getPracticeQuestion(String topicName) {
        return getPracticeQuestion(topicName, null);
    }

    @Override
    public String getPracticeQuestion(String topicName, String subjectName) {
        if (topicName == null) return "Describe the core principles of this concept and outline an example.";
        String lower = topicName.toLowerCase();

        if (isWebTech(topicName, subjectName)) {
            if (lower.contains("html")) {
                return "Explain why semantic HTML5 tags (e.g. <article>, <nav>, <section>) are preferable over generic <div> tags for SEO and accessibility.";
            }
            if (lower.contains("css")) {
                return "Differentiate between CSS Flexbox (one-dimensional) and CSS Grid (two-dimensional). When would you choose each for page layout?";
            }
            if (lower.contains("multitier") || lower.contains("architecture")) {
                return "Describe the 3 tiers of a web application. What are the advantages of separating the presentation tier from the application logic tier?";
            }
            return "Explain the lifecycle of an HTTP request from URL entry in the browser to full DOM rendering. How do cookies and sessions maintain state?";
        }

        if (lower.contains("linked list"))
            return "Write a function to reverse a singly linked list in-place using O(1) extra space. " +
                   "Trace through the list: 1→2→3→4→5→NULL";
        if (lower.contains("stack"))
            return "Implement a queue using two stacks. What is the amortized time complexity of each operation?";
        if (lower.contains("bst"))
            return "Given a BST, write a function to find the k-th smallest element without using extra space.";
        if (lower.contains("big-o"))
            return "Analyze the time and space complexity of this nested loop:\n" +
                   "for(i=0;i<n;i++) for(j=i;j<n;j++) sum += arr[i]+arr[j];";
        if (lower.contains("scheduling"))
            return "Given processes P1(burst=6), P2(burst=8), P3(burst=7), P4(burst=3) arriving at t=0,1,2,3: " +
                   "Calculate average waiting time for Round Robin with quantum=2.";

        return "Summarize the core principles of this topic, list 3 practical applications, " +
               "and describe the key factors to consider when implementing it.";
    }

    private boolean isWebTech(String topic, String subject) {
        String combined = ((topic != null ? topic : "") + " " + (subject != null ? subject : "")).toLowerCase();
        return combined.matches(".*\\b(web|html|html5|css|javascript|js|fwt|frontend|backend|client|server|http|browser|dom|ajax|json|bootstrap|php|rest|api|multitier)\\b.*");
    }

    private boolean isDsa(String topic, String subject) {
        String combined = ((topic != null ? topic : "") + " " + (subject != null ? subject : "")).toLowerCase();
        return combined.matches(".*\\b(tree|bst|heap|graph|stack|queue|linked list|sorting|search|complexity|algorithm|dsa|big-o|traversal|recursion)\\b.*");
    }

    private boolean isOs(String topic, String subject) {
        String combined = ((topic != null ? topic : "") + " " + (subject != null ? subject : "")).toLowerCase();
        return combined.matches(".*\\b(os|operating system|process|thread|deadlock|scheduling|paging|virtual memory|kernel)\\b.*");
    }

    private boolean isDbms(String topic, String subject) {
        String combined = ((topic != null ? topic : "") + " " + (subject != null ? subject : "")).toLowerCase();
        return combined.matches(".*\\b(dbms|database|sql|relational|rdbms|transaction|acid|query|normalization)\\b.*");
    }

    private String getDefaultExplanation() {
        return "This topic covers fundamental concepts that are essential for understanding " +
               "the broader subject. Focus on the definition, core properties, and how it " +
               "relates to concepts you have already learned. Building a strong mental model " +
               "now will make advanced topics significantly easier.";
    }
}
