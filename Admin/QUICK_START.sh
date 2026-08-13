#!/bin/bash
# ============================================
# UNINEST LANDLORD PORTAL - QUICK START GUIDE
# Copy & paste commands for Windows PowerShell
# ============================================

# 📋 STEP 1: VERIFY JAVA INSTALLATION
# Expected Output: java version "25"
java -version

# 📋 STEP 2: VERIFY MYSQL INSTALLATION  
# Expected Output: MySQL version info
mysql --version

# 📋 STEP 3: START MYSQL SERVICE
# For Windows:
net start MySQL80
# Or if you have different version:
net start "MySQL"

# 📋 STEP 4: CREATE DATABASE
# Run this command:
mysql -u root -p -e "CREATE DATABASE uninest; SHOW DATABASES;"

# 📋 STEP 5: IMPORT SCHEMA
# Run this command:
mysql -u root -p uninest < "C:\Users\DELL I5\Videos\Uninest Website\UniNest-Landlord\database\uninest_complete_schema.sql"

# 📋 STEP 6: VERIFY DATABASE
# Run in MySQL:
# USE uninest;
# SHOW TABLES;
# SELECT COUNT(*) FROM amenities;  -- Should be 10

# 📋 STEP 7: CONFIRM FILE STRUCTURE
# Open Explorer and verify:
# ✓ C:\Users\DELL I5\Videos\Uninest Website\UniNest-Landlord\src\java\com\uninest\
# ✓ C:\Users\DELL I5\Videos\Uninest Website\UniNest-Landlord\web\jsp\
# ✓ C:\Users\DELL I5\Videos\Uninest Website\UniNest-Landlord\web\css\
# ✓ C:\Users\DELL I5\Videos\Uninest Website\UniNest-Landlord\database\

# 📋 STEP 8: NETBEANS CONFIGURATION
# 1. Open NetBeans
# 2. File → Open Project
# 3. Navigate to: C:\Users\DELL I5\Videos\Uninest Website\UniNest-Landlord
# 4. Click Open Project
# 5. Wait for project to fully load

# 📋 STEP 9: ADD MYSQL DRIVER TO PROJECT
# 1. Right-click "UniNest-Landlord" in Projects
# 2. Properties
# 3. Libraries → Add JAR/Folder
# 4. Select: mysql-connector-java-8.0.X.jar file
# 5. Click OK

# 📋 STEP 10: CONFIGURE DATABASE CONNECTION
# 1. Open: src → com.uninest.utils → DatabaseConnection.java
# 2. Find lines 15-17
# 3. Update:
#    DB_URL = "jdbc:mysql://localhost:3306/uninest"
#    DB_USER = "root"
#    DB_PASSWORD = "your_password"
# 4. Save (Ctrl+S)

# 📋 STEP 11: BUILD & RUN
# 1. Right-click Project → Clean and Build
# 2. Right-click Project → Run (Shift+F6)
# 3. Browser should open: http://localhost:8080/UniNest-Landlord/

# ============================================
# TESTING CHECKLIST
# ============================================

echo "Testing Application..."

# Test 1: Check Login Page
echo "✓ Test 1: Access Login Page"
echo "  URL: http://localhost:8080/UniNest-Landlord/Login.jsp"
echo "  Expected: Professional login form visible"

# Test 2: Check Register Page
echo "✓ Test 2: Access Register Page"
echo "  URL: http://localhost:8080/UniNest-Landlord/Register.jsp"
echo "  Expected: Registration form with file upload"

# Test 3: Register New Landlord
echo "✓ Test 3: Test Registration"
echo "  1. Fill registration form"
echo "  2. Upload ID document"
echo "  3. Click 'Create Account'"
echo "  4. Check MySQL: SELECT * FROM users WHERE email='your_email@test.com';"

# Test 4: Login
echo "✓ Test 4: Test Login"
echo "  1. Enter registered email"
echo "  2. Enter password"
echo "  3. Should redirect to Dashboard"
echo "  4. Check MySQL: SELECT last_login FROM users WHERE email='your_email@test.com';"

# Test 5: Add Property
echo "✓ Test 5: Test Add Property"
echo "  1. From Dashboard, click 'Add Property'"
echo "  2. Fill property details"
echo "  3. Select amenities"
echo "  4. Upload property images"
echo "  5. Click 'Add Property'"
echo "  6. Check MySQL: SELECT * FROM rooms WHERE landlord_id=1;"

# Test 6: Database Integrity
echo "✓ Test 6: Verify Database"
echo "  Run in MySQL:"
echo "  USE uninest;"
echo "  SHOW TABLES;  -- Should show 15 tables"
echo "  SELECT COUNT(*) FROM amenities;  -- Should be 10"

# ============================================
# TROUBLESHOOTING
# ============================================

echo ""
echo "🔧 TROUBLESHOOTING COMMANDS:"
echo ""

echo "Check MySQL Service:"
echo "  sc query mysql80"
echo ""

echo "Start MySQL Service:"
echo "  net start mysql80"
echo ""

echo "Stop MySQL Service:"
echo "  net stop mysql80"
echo ""

echo "Connect to MySQL:"
echo "  mysql -u root -p"
echo ""

echo "List all databases:"
echo "  mysql -u root -p -e 'SHOW DATABASES;'"
echo ""

echo "Backup UniNest Database:"
echo "  mysqldump -u root -p uninest > uninest_backup.sql"
echo ""

echo "Restore UniNest Database:"
echo "  mysql -u root -p uninest < uninest_backup.sql"
echo ""

# ============================================
# COMMON ERRORS & SOLUTIONS
# ============================================

echo "❌ ERROR: Connection refused"
echo "   SOLUTION: Start MySQL service (net start mysql80)"
echo ""

echo "❌ ERROR: JDBC Driver not found"
echo "   SOLUTION: Add mysql-connector-java jar to libraries"
echo ""

echo "❌ ERROR: 404 Not Found"
echo "   SOLUTION: Check servlet mapping, rebuild project"
echo ""

echo "❌ ERROR: Authentication failed"
echo "   SOLUTION: Verify DB credentials in DatabaseConnection.java"
echo ""

echo "❌ ERROR: No tables in database"
echo "   SOLUTION: Run uninest_complete_schema.sql"
echo ""

# ============================================
# USEFUL MYSQL QUERIES
# ============================================

echo "📊 USEFUL DATABASE QUERIES:"
echo ""

echo "View all users:"
echo "  SELECT user_id, email, role, status FROM users;"
echo ""

echo "View all landlords:"
echo "  SELECT l.*, u.email FROM landlords l JOIN users u ON l.user_id=u.user_id;"
echo ""

echo "View all properties:"
echo "  SELECT r.*, u.full_name FROM rooms r JOIN landlords l ON r.landlord_id=l.landlord_id JOIN users u ON l.user_id=u.user_id;"
echo ""

echo "View all bookings:"
echo "  SELECT * FROM bookings WHERE booking_status='pending';"
echo ""

echo "View all messages:"
echo "  SELECT * FROM messages WHERE is_read=FALSE;"
echo ""

echo "Check password hash:"
echo "  SELECT user_id, email, password FROM users LIMIT 1;"
echo ""

# ============================================
# NETBEANS KEYBOARD SHORTCUTS
# ============================================

echo "⌨️  USEFUL NETBEANS SHORTCUTS:"
echo ""
echo "  Ctrl+S     = Save file"
echo "  Ctrl+Shift+F = Format code"
echo "  Ctrl+/     = Comment/uncomment"
echo "  F6         = Run project"
echo "  Shift+F6   = Run with arguments"
echo "  Ctrl+Shift+B = Build project"
echo "  F11        = Toggle breakpoint (debugging)"
echo "  Ctrl+Shift+I = Fix imports"
echo ""

# ============================================
# FINAL STATUS
# ============================================

echo "✅ SYSTEM SETUP COMPLETE!"
echo ""
echo "Next Steps:"
echo "1. Open NetBeans"
echo "2. Import UniNest-Landlord project"
echo "3. Add MySQL JDBC driver"
echo "4. Configure DatabaseConnection.java"
echo "5. Run project (F6)"
echo ""
echo "The system is now ready for:
echo "- Testing existing functionality"
echo "- Creating remaining JSP pages"
echo "- Creating additional servlets"
echo "- Full integration testing"
echo ""
echo "Estimated completion: 5 more hours of development"
echo ""
echo "Good luck! You've got this! 🚀"
