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
import org.springframework.jdbc.core.JdbcTemplate;

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
    @Autowired JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) throws Exception {

        // ======== ROLES ========
        List<String> requiredRoles = Arrays.asList("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_HR_MANAGER", "ROLE_MANAGER", "ROLE_EMPLOYEE", "ROLE_INTERNSHIP_SUPERVISOR");
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
        Designation internSup = getOrCreateDesig("Internship Supervisor");

        // ======== LEAVE TYPES ========
        LeaveType annual    = getOrCreateLeaveType("Annual Leave",    true,  true,  10, 21);
        LeaveType sick      = getOrCreateLeaveType("Sick Leave",      true,  false, 0,  10);
        LeaveType casual    = getOrCreateLeaveType("Casual Leave",    true,  false, 0,  7);
        LeaveType study     = getOrCreateLeaveType("Study Leave",     true,  false, 0,  14);
        LeaveType noPay     = getOrCreateLeaveType("No Pay Leave",    false, false, 0,  0);
        LeaveType maternity = getOrCreateLeaveType("Maternity Leave", true,  false, 0,  90);
        LeaveType duty      = getOrCreateLeaveType("Duty Leave",      true,  false, 0,  0);
        LeaveType official  = getOrCreateLeaveType("Official Leave",  true,  false, 0,  0);

        // ======== USERS & EMPLOYEES ========
        // 1. Default Super Admin
        User superAdminUser = getOrCreateUser("superadmin", "admin123", "ROLE_SUPER_ADMIN");
        Employee superAdminEmp = getOrCreateEmployee(superAdminUser, "Super", "Admin",
                "superadmin@company.com", it, ceo, null, "FULL_TIME");
        seedLeaveBalances(superAdminEmp, annual, sick, casual, study);
        seedAttendanceToday(superAdminEmp);

        // 2. Sachie Nelanka (Super Admin)
        User sachieUser = getOrCreateUser("sachie.nelanka", "sachie123", "ROLE_SUPER_ADMIN");
        Employee sachieEmp = getOrCreateEmployee(sachieUser, "Sachie", "Nelanka",
                "sachie.nelanka@company.com", hr, hrMgr, null, "FULL_TIME");
        seedLeaveBalances(sachieEmp, annual, sick, casual, study);
        seedAttendanceToday(sachieEmp);

        System.out.println("[Seeder] ✅ All seed data loaded successfully.");
        System.out.println("[Seeder] Users: superadmin/admin123, sachie.nelanka/sachie123 (Both are Super Admins)");

        // Set the starting AUTO_INCREMENT for the employees table to 26
        try {
            jdbcTemplate.execute("ALTER TABLE employees AUTO_INCREMENT = 26");
            System.out.println("[Seeder] ✅ Employees AUTO_INCREMENT set to 26.");
        } catch (Exception e) {
            System.err.println("[Seeder] ⚠️ Could not set AUTO_INCREMENT. Ignore if using H2/PostgreSQL without MySQL dialect.");
        }
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
