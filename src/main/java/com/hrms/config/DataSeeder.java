package com.hrms.config;

import com.hrms.attendance.Attendance;
import com.hrms.attendance.AttendanceRepository;
import com.hrms.department.Department;
import com.hrms.department.DepartmentRepository;
import com.hrms.department.Designation;
import com.hrms.department.DesignationRepository;
import com.hrms.employee.Employee;
import com.hrms.employee.EmployeeRepository;
import com.hrms.leave.*;
import com.hrms.user.Role;
import com.hrms.user.RoleRepository;
import com.hrms.user.User;
import com.hrms.user.UserRepository;
import com.hrms.task.DailyTask;
import com.hrms.task.DailyTaskRepository;
import com.hrms.leave.LeaveRequest;
import com.hrms.leave.LeaveRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired RoleRepository roleRepo;
    @Autowired UserRepository userRepo;
    @Autowired DepartmentRepository deptRepo;
    @Autowired DesignationRepository desigRepo;
    @Autowired EmployeeRepository empRepo;
    @Autowired LeaveTypeRepository leaveTypeRepo;
    @Autowired LeaveBalanceRepository leaveBalanceRepo;
    @Autowired AttendanceRepository attendanceRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired DailyTaskRepository dailyTaskRepo;
    @Autowired LeaveRequestRepository leaveRequestRepo;

    @Override
    @Transactional
    public void run(String... args) throws Exception {

        // ======== ROLES ========
        List<String> requiredRoles = Arrays.asList("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_HR_MANAGER", "ROLE_MANAGER", "ROLE_EMPLOYEE");
        for (String name : requiredRoles) {
            if (roleRepo.findByName(name).isEmpty()) {
                Role r = new Role();
                r.setName(name);
                roleRepo.save(r);
            }
        }
        System.out.println("[Seeder] Roles verified/created.");

        // ======== DEPARTMENTS ========
        Department it     = getOrCreateDept("IT Department", "Information Technology");
        Department hr     = getOrCreateDept("Human Resources", "People and Culture");
        Department fin    = getOrCreateDept("Finance", "Finance and Accounts");
        Department ops    = getOrCreateDept("Operations", "Business Operations");
        Department sales  = getOrCreateDept("Sales", "Sales and Marketing");

        // ======== DESIGNATIONS ========
        Designation ceo       = getOrCreateDesig("Chief Executive Officer");
        Designation hrMgr     = getOrCreateDesig("HR Manager");
        Designation swe       = getOrCreateDesig("Software Engineer");
        Designation salesMgr  = getOrCreateDesig("Sales Manager");
        Designation accountant= getOrCreateDesig("Accountant");

        // ======== LEAVE TYPES ========
        LeaveType annual    = getOrCreateLeaveType("Annual Leave",    true,  true,  10, 21);
        LeaveType sick      = getOrCreateLeaveType("Sick Leave",      true,  false, 0,  10);
        LeaveType casual    = getOrCreateLeaveType("Casual Leave",    true,  false, 0,  7);
        LeaveType study     = getOrCreateLeaveType("Study Leave",     true,  false, 0,  14);
        LeaveType noPay     = getOrCreateLeaveType("No Pay Leave",    false, false, 0,  0);
        LeaveType maternity = getOrCreateLeaveType("Maternity Leave", true,  false, 0,  90);

        // ======== USERS & EMPLOYEES ========
        // Super Admin
        User superAdminUser = getOrCreateUser("superadmin", "admin123", "ROLE_SUPER_ADMIN");
        Employee superAdminEmp = getOrCreateEmployee(superAdminUser, "Super", "Admin",
                "superadmin@company.com", it, ceo, null, "FULL_TIME");
        seedLeaveBalances(superAdminEmp, annual, sick, casual, study);
        seedAttendanceToday(superAdminEmp);

        // Admin
        User adminUser = getOrCreateUser("admin", "admin123", "ROLE_ADMIN");
        Employee adminEmp = getOrCreateEmployee(adminUser, "Super", "Admin",
                "admin@company.com", it, ceo, null, "FULL_TIME");
        seedLeaveBalances(adminEmp, annual, sick, casual, study);
        seedAttendanceToday(adminEmp);

        // HR Manager
        User hrUser = getOrCreateUser("hrmanager", "hr123", "ROLE_HR_MANAGER");
        Employee hrEmp = getOrCreateEmployee(hrUser, "Sara", "Johnson",
                "sara.johnson@company.com", hr, hrMgr, null, "FULL_TIME");
        seedLeaveBalances(hrEmp, annual, sick, casual, study);
        seedAttendanceToday(hrEmp);

        // Manager
        User managerUser = getOrCreateUser("manager", "manager123", "ROLE_MANAGER");
        Employee managerEmp = getOrCreateEmployee(managerUser, "David", "Williams",
                "david.williams@company.com", it, swe, null, "FULL_TIME");
        seedLeaveBalances(managerEmp, annual, sick, casual, study);
        seedAttendanceToday(managerEmp);

        // Employee 1 — reports to manager
        User emp1User = getOrCreateUser("employee1", "emp123", "ROLE_EMPLOYEE");
        Employee emp1 = getOrCreateEmployee(emp1User, "Alice", "Brown",
                "alice.brown@company.com", it, swe, managerEmp, "FULL_TIME");
        seedLeaveBalances(emp1, annual, sick, casual, study);

        // Employee 2
        User emp2User = getOrCreateUser("employee2", "emp123", "ROLE_EMPLOYEE");
        Employee emp2 = getOrCreateEmployee(emp2User, "Bob", "Martinez",
                "bob.martinez@company.com", sales, salesMgr, null, "FULL_TIME");
        seedLeaveBalances(emp2, annual, sick, casual, study);

        // Employee 3
        User emp3User = getOrCreateUser("employee3", "emp123", "ROLE_EMPLOYEE");
        Employee emp3 = getOrCreateEmployee(emp3User, "Clara", "Lee",
                "clara.lee@company.com", fin, accountant, null, "PART_TIME");
        seedLeaveBalances(emp3, annual, sick, casual, study);

        // ======== EXTRA DUMMY DATA FOR TESTING SCENARIOS ========
        // 1. Jane Doe (newemployee) - reports to manager, has no attendance today, has pending/approved/rejected tasks, pending/approved leaves
        User newEmpUser = getOrCreateUser("newemployee", "emp123", "ROLE_EMPLOYEE");
        Employee newEmp = getOrCreateEmployee(newEmpUser, "Jane", "Doe",
                "jane.doe@company.com", it, swe, managerEmp, "CONTRACT");
        seedLeaveBalances(newEmp, annual, sick, casual, study);
        // Note: NO attendance today so they can test Clock In!

        // Seed tasks for newemployee
        getOrCreateDailyTask(newEmp, LocalDate.now().minusDays(1), 
                "Implemented user login authentication endpoint and integration tests.", 
                "APPROVED", 0.0, "Excellent work, tests passed successfully.", managerEmp);
        getOrCreateDailyTask(newEmp, LocalDate.now(), 
                "Refactored LeaveController and added validation logic for holidays.", 
                "PENDING", 0.0, null, null);
        getOrCreateDailyTask(newEmp, LocalDate.now().minusDays(2), 
                "Created test cases for AttendanceController.", 
                "REJECTED", 0.5, "Please add more assertions and edge cases.", managerEmp);

        // Seed leave requests for newemployee
        getOrCreateLeaveRequest(newEmp, study, LocalDate.now().plusDays(2), LocalDate.now().plusDays(4), 
                3.0, "University Final Exams", "PENDING", null);
        getOrCreateLeaveRequest(newEmp, casual, LocalDate.now().minusDays(10), LocalDate.now().minusDays(8), 
                2.0, "Family function", "APPROVED", managerEmp);

        // 2. Bob Miller (rejectedemp) - has multiple rejected tasks to show cumulative extension days
        User rejectedEmpUser = getOrCreateUser("rejectedemp", "emp123", "ROLE_EMPLOYEE");
        Employee rejectedEmp = getOrCreateEmployee(rejectedEmpUser, "Bob", "Miller",
                "bob.miller@company.com", it, swe, managerEmp, "CONTRACT");
        seedLeaveBalances(rejectedEmp, annual, sick, casual, study);
        seedAttendanceToday(rejectedEmp);

        getOrCreateDailyTask(rejectedEmp, LocalDate.now().minusDays(3), 
                "UI layout design for user profile page", 
                "REJECTED", 1.0, "Design does not match technical specs. Please revise.", managerEmp);
        getOrCreateDailyTask(rejectedEmp, LocalDate.now().minusDays(2), 
                "Added mock APIs for employee registration", 
                "REJECTED", 0.5, "Mock data is missing key fields.", managerEmp);
        getOrCreateDailyTask(rejectedEmp, LocalDate.now().minusDays(1), 
                "Fixed bugs in employee registration form", 
                "APPROVED", 0.0, "Looks good now.", managerEmp);

        // 3. Alice Johnson (newmanager) - ROLE_MANAGER, IT Department, reports to null
        User newMgrUser = getOrCreateUser("newmanager", "manager123", "ROLE_MANAGER");
        Employee newMgr = getOrCreateEmployee(newMgrUser, "Alice", "Johnson",
                "alice.johnson@company.com", it, swe, null, "FULL_TIME");
        seedLeaveBalances(newMgr, annual, sick, casual, study);
        seedAttendanceToday(newMgr);

        // 4. Charlie Green (newhr) - ROLE_HR_MANAGER, HR Department, reports to null
        User newHrUser = getOrCreateUser("newhr", "hr123", "ROLE_HR_MANAGER");
        Employee newHr = getOrCreateEmployee(newHrUser, "Charlie", "Green",
                "charlie.green@company.com", hr, hrMgr, null, "FULL_TIME");
        seedLeaveBalances(newHr, annual, sick, casual, study);
        seedAttendanceToday(newHr);

        System.out.println("[Seeder] ✅ All seed data loaded successfully.");
        System.out.println("[Seeder] Users: admin/admin123, hrmanager/hr123, manager/manager123, employee1-3/emp123, newemployee/emp123, rejectedemp/emp123, newmanager/manager123, newhr/hr123");
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private Department getOrCreateDept(String name, String description) {
        return deptRepo.findByName(name).orElseGet(() -> {
            Department d = new Department();
            d.setName(name);
            d.setDescription(description);
            return deptRepo.save(d);
        });
    }

    private Designation getOrCreateDesig(String title) {
        return desigRepo.findByTitle(title).orElseGet(() -> {
            Designation d = new Designation();
            d.setTitle(title);
            return desigRepo.save(d);
        });
    }

    private LeaveType getOrCreateLeaveType(String name, boolean paid, boolean carryForward, int maxCarry, int daysPerYear) {
        return leaveTypeRepo.findByName(name).orElseGet(() -> {
            LeaveType lt = new LeaveType();
            lt.setName(name);
            lt.setPaid(paid);
            lt.setCarryForwardAllowed(carryForward);
            lt.setMaxCarryForwardDays(maxCarry);
            lt.setDaysPerYear(daysPerYear);
            lt.setHalfDayAllowed(true);
            return leaveTypeRepo.save(lt);
        });
    }

    private User getOrCreateUser(String username, String password, String roleName) {
        return userRepo.findByUsername(username).orElseGet(() -> {
            User u = new User();
            u.setUsername(username);
            u.setPassword(encoder.encode(password));
            u.setRole(roleRepo.findByName(roleName).orElseThrow());
            return userRepo.save(u);
        });
    }

    private Employee getOrCreateEmployee(User user, String firstName, String lastName, String email,
                                         Department dept, Designation desig, Employee manager, String empType) {
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            user.setEmail(email);
            userRepo.save(user);
        }
        return empRepo.findByUserId(user.getId()).orElseGet(() -> {
            Employee e = new Employee();
            e.setUser(user);
            e.setFirstName(firstName);
            e.setLastName(lastName);
            e.setPersonalEmail(email);
            e.setDepartment(dept);
            e.setDesignation(desig);
            e.setManager(manager);
            e.setJoiningDate(LocalDate.of(2023, 1, 15));
            e.setEmploymentType(empType);
            e.setStatus("ACTIVE");
            e.setGender("N/A");
            e.setPhoneNumber("+1 555 000 0000");
            e.setAddress("123 Main St, City");
            e.setEmergencyContactName("Emergency Contact");
            e.setEmergencyContactPhone("+1 555 999 0000");
            return empRepo.save(e);
        });
    }

    private void seedLeaveBalances(Employee emp, LeaveType... types) {
        int year = LocalDate.now().getYear();
        for (LeaveType lt : types) {
            boolean exists = leaveBalanceRepo
                    .findByEmployeeIdAndLeaveTypeIdAndYear(emp.getId(), lt.getId(), year)
                    .isPresent();
            if (!exists) {
                LeaveBalance lb = new LeaveBalance();
                lb.setEmployee(emp);
                lb.setLeaveType(lt);
                lb.setYear(year);
                double days = "Annual Leave".equals(lt.getName()) ? 21 :
                              "Sick Leave".equals(lt.getName()) ? 10 :
                              "Study Leave".equals(lt.getName()) ? 14 : 7;
                lb.setAllocatedDays(days);
                lb.setUsedDays(0);
                lb.setCarriedForwardDays(0);
                leaveBalanceRepo.save(lb);
            }
        }
    }

    private void seedAttendanceToday(Employee emp) {
        LocalDate today = LocalDate.now();
        boolean exists = attendanceRepo.findByEmployeeIdAndWorkDate(emp.getId(), today).isPresent();
        if (!exists) {
            Attendance att = new Attendance();
            att.setEmployee(emp);
            att.setWorkDate(today);
            att.setCheckInTime(LocalTime.of(9, 0));
            att.setStatus("PRESENT");
            att.setLate(false);
            attendanceRepo.save(att);
        }
    }

    private void getOrCreateDailyTask(Employee emp, LocalDate date, String desc, String status, double extensionDays, String remarks, Employee reviewer) {
        boolean exists = dailyTaskRepo.findByEmployeeIdOrderByTaskDateDesc(emp.getId())
                .stream()
                .anyMatch(t -> t.getTaskDate().equals(date));
        if (!exists) {
            DailyTask task = new DailyTask();
            task.setEmployee(emp);
            task.setTaskDate(date);
            task.setTaskDescription(desc);
            task.setStatus(status);
            task.setExtensionDays(extensionDays);
            task.setRemarks(remarks);
            task.setReviewedBy(reviewer);
            if (!"PENDING".equals(status)) {
                task.setReviewedAt(java.time.LocalDateTime.now());
            }
            dailyTaskRepo.save(task);
            
            // Accumulate extension days if rejected
            if ("REJECTED".equals(status) && extensionDays > 0) {
                double current = emp.getTotalExtensionDays() == null ? 0 : emp.getTotalExtensionDays();
                emp.setTotalExtensionDays(current + extensionDays);
                empRepo.save(emp);
            }
        }
    }

    private void getOrCreateLeaveRequest(Employee emp, LeaveType type, LocalDate start, LocalDate end, double days, String reason, String status, Employee approvedBy) {
        boolean exists = leaveRequestRepo.findByEmployeeId(emp.getId())
                .stream()
                .anyMatch(r -> r.getStartDate().equals(start) && r.getEndDate().equals(end));
        if (!exists) {
            LeaveRequest req = new LeaveRequest();
            req.setEmployee(emp);
            req.setLeaveType(type);
            req.setStartDate(start);
            req.setEndDate(end);
            req.setLeaveDays(days);
            req.setReason(reason);
            req.setStatus(status);
            if ("APPROVED".equals(status)) {
                req.setApprovedBy(approvedBy);
                req.setApprovalDate(java.time.LocalDateTime.now());
            }
            leaveRequestRepo.save(req);
        }
    }
}
