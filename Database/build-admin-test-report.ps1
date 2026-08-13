$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root 'Admin_Testing_Document.doc'
$servletRoot = Join-Path $root 'Admin\src\java\com\uninest\servlets'

function HtmlEncode([string]$s) { [System.Net.WebUtility]::HtmlEncode($s) }
function Row($cells, $header=$false) {
  $tag = if($header){'th'}else{'td'}
  '<tr>' + (($cells | ForEach-Object { "<$tag>$(HtmlEncode $_)</$tag>" }) -join '') + '</tr>'
}

$tests = @(
@('ADM-FE-01','Frontend','Login','Open Admin login URL','Login form loads with email, password and submit controls','Page does not load, styling is missing, or fields overlap'),
@('ADM-BE-01','Backend','LoginServlet','Submit valid active administrator credentials','Session is created, last_login is updated and Dashboard opens','Incorrect query, database unavailable, invalid role/status, or redirect fails'),
@('ADM-BE-02','Backend','LoginServlet','Submit an incorrect password','Access is denied and a clear error is shown','Invalid user is authenticated or raw exception is displayed'),
@('ADM-BE-03','Backend','LoginServlet','Submit empty or malformed fields','Validation prevents authentication','Blank input reaches database or produces server error'),
@('ADM-FE-02','Frontend','Dashboard','Open Dashboard after login','Navigation, review summary and pending table render correctly','Broken links, missing components or unauthorized access'),
@('ADM-FE-03','Frontend','Navigation','Open every sidebar destination','Each link opens the correct Admin page under /uninest','404 response, wrong context path or incorrect active state'),
@('ADM-BE-04','Backend','AdminPageServlet','Request mapped Admin routes','Route redirects to the correct Admin HTML page','Unknown route is exposed or valid route returns 404'),
@('ADM-FE-04','Frontend','Listing Queue','Search and filter listing submissions','Queue updates without layout overflow','Search ignored, filter incorrect, loading state never resolves'),
@('ADM-BE-05','Backend','AdminListingQueueServlet','Request queue as authenticated admin','JSON contains paginated listings and total count','401, 500, malformed JSON or incorrect pagination'),
@('ADM-BE-06','Backend','AdminListingQueueServlet','Request queue without admin session','HTTP 401 is returned','Private listing data is exposed'),
@('ADM-FE-05','Frontend','Listing Verification','Open a listing and review details','Property, landlord, rent, location and evidence are visible','Wrong record, missing data or broken image/layout'),
@('ADM-BE-07','Backend','VerifyPropertyServlet','Approve a valid pending listing','rooms approval_status becomes approved and decision is confirmed','No row updated, invalid status or database error'),
@('ADM-BE-08','Backend','VerifyPropertyServlet','Decline a listing with an admin note','Status and note are saved','Reason is lost or listing remains pending'),
@('ADM-BE-09','Backend','EditPropertyServlet','Submit permitted listing corrections','Selected room is updated and success is displayed','Unauthorized room is updated or invalid values are stored'),
@('ADM-FE-06','Frontend','Booking Requests','Open and filter booking requests','Current bookings and statuses are readable','Records missing, controls fail or table overflows'),
@('ADM-BE-10','Backend','ApproveBookingServlet','Approve a pending booking','Booking status updates and student notification is created','Booking unchanged or notification missing'),
@('ADM-BE-11','Backend','BookingActionServlet','Decline/cancel booking with reason','Status and cancellation reason are saved','Invalid transition accepted or reason lost'),
@('ADM-FE-07','Frontend','Enquiries','Open enquiries and select a status','Enquiries display and status action is available','Wrong record or unusable empty/error state'),
@('ADM-BE-12','Backend','EnquiriesServlet','Update an enquiry status','inquiries.reply_status updates','Invalid status accepted or no row updated'),
@('ADM-FE-08','Frontend','Messages','Open messages and compose a reply','Conversation and message validation behave correctly','Composer fails, message disappears or layout breaks'),
@('ADM-BE-13','Backend','SendMessageServlet','Send a valid non-empty message','Message and recipient notification are inserted','Empty message inserted, invalid recipient or database failure'),
@('ADM-BE-14','Backend','MessagesServlet','Mark a message as read','is_read and read_date update','Message remains unread or another message is changed'),
@('ADM-FE-09','Frontend','Maintenance','Open and filter maintenance requests','Requests and resolution controls display','Missing records or status controls fail'),
@('ADM-BE-15','Backend','MaintenanceServlet','Change request status and notes','maintenance_requests is updated','Invalid status, lost resolution notes or wrong row update'),
@('ADM-FE-10','Frontend','Records / Payments','Open activity records and a detail view','Records render and selected detail is consistent','Incorrect record, broken link or empty state misleading'),
@('ADM-BE-16','Backend','PaymentsServlet','Update payment status and receipt reference','payments fields update consistently','Invalid payment state or receipt not saved'),
@('ADM-BE-17','Backend','NotificationServlet','Mark one/all notifications read','Correct notification rows receive read timestamps','Wrong user records updated or action fails'),
@('ADM-FE-11','Frontend','Profile','Edit administrator profile fields','Form is populated, validates input and confirms save','Fields reset, invalid email accepted or feedback missing'),
@('ADM-BE-18','Backend','ProfileServlet','Save valid profile changes','users record and session display values update','Duplicate/invalid data accepted or record not updated'),
@('ADM-FE-12','Frontend','Forgot Password','Submit registered email','Neutral confirmation is shown','Account existence is exposed or request crashes'),
@('ADM-BE-19','Backend','ForgotPasswordServlet','Submit registered and unknown emails','Registered request creates notification; response remains safe','SQL error, account enumeration or no notification'),
@('ADM-FE-13','Frontend','Admin Access Request','Submit access request form','Required-field validation and confirmation work','Invalid submission accepted or raw backend message shown'),
@('ADM-BE-20','Backend','AdminRegisterRequestServlet','Submit valid admin request','Notification is recorded for review','Request lost, duplicate mishandled or database error'),
@('ADM-BE-21','Backend','DatabaseStatusServlet','Request health endpoint while MySQL runs','Endpoint reports connected status','Driver missing, credentials invalid or database unavailable'),
@('ADM-BE-22','Backend','DatabaseConnection','Start Tomcat with MySQL running','Log records Database connection established','Connection timeout, access denied or unknown database'),
@('ADM-BE-23','Backend','LogoutServlet','Sign out from an authenticated session','Session is invalidated and login page opens','Protected session remains active'),
@('ADM-NF-01','Frontend','Responsive UI','Repeat core pages at mobile width','No overlap or horizontal page overflow','Controls overlap, clipped text or inaccessible navigation'),
@('ADM-NF-02','Security','Session protection','Open protected actions without login','Request redirects or returns 401','Admin action executes anonymously'),
@('ADM-NF-03','Security','SQL input handling','Submit quotes and SQL-like text in inputs','Prepared statements treat input as data','SQL error or unintended database change'),
@('ADM-NF-04','Recovery','Database unavailable','Stop MySQL temporarily and repeat health/login request','Friendly failure; Tomcat remains operational','Stack trace exposed or application becomes unusable')
)

$html = @"
<html><head><meta charset='utf-8'><style>
@page{size:A4 landscape;margin:1.2cm}body{font-family:Arial;color:#17212b;font-size:9pt;line-height:1.25}h1{color:#243c8f;font-size:25pt;margin:0 0 4pt}h2{color:#243c8f;font-size:16pt;border-bottom:2px solid #22c55e;padding-bottom:4pt;page-break-before:always}h2.first{page-break-before:auto}h3{color:#1e3278;font-size:12pt;margin-top:14pt}.meta{color:#52606d;font-size:10pt}.callout{background:#eef4ff;border-left:5px solid #22c55e;padding:10pt;margin:12pt 0}table{border-collapse:collapse;width:100%;margin:6pt 0 14pt;table-layout:fixed}th{background:#243c8f;color:white;font-weight:bold;padding:5pt;border:1px solid #1e3278}td{padding:5pt;border:1px solid #b8c4ca;vertical-align:top;word-wrap:break-word}.small{font-size:8pt}.status{width:5%}.code{font-family:Consolas;background:#17212b;color:#f8fafc;border:1px solid #243c8f;padding:9pt;white-space:pre-wrap;font-size:7.5pt;page-break-inside:avoid}.fig{font-weight:bold;color:#243c8f;margin-top:9pt}.pb{page-break-before:always}ul,ol{margin-top:3pt}.check{font-size:11pt}footer{color:#52606d}</style></head><body>
<h1>UniNest Admin System Test Report</h1>
<p class='meta'><b>Scope:</b> Admin frontend, Java servlet backend, MySQL integration and failure handling<br><b>Environment:</b> NetBeans 22 | JDK 11 | Apache Tomcat 9 | MySQL 8 | Context /uninest<br><b>Prepared:</b> $(Get-Date -Format 'dd MMMM yyyy')</p>
<div class='callout'><b>How to use this report:</b> Execute each row, enter the Actual Result, mark Pass or Fail, attach the named evidence, and complete the failure fields only when a test fails. Do not claim a pass until the action and database effect have both been observed.</div>

<h2 class='first'>1. Test Result Fields</h2>
<table><tr><th>Field</th><th>What to record</th></tr>
<tr><td>Actual Result</td><td>What the interface, Tomcat log and database actually showed.</td></tr>
<tr><td>Pass / Fail</td><td>Pass only when actual result matches expected result.</td></tr>
<tr><td>Failure Observed</td><td>Visible symptom, exact error text and time of failure.</td></tr>
<tr><td>Error Evidence</td><td>Screenshot filename, Tomcat log line, SQL query result or video timestamp.</td></tr>
<tr><td>Corrective Action</td><td>Configuration or code change used to resolve the failure.</td></tr>
<tr><td>Retest Result</td><td>Result after the fix, including new evidence reference.</td></tr></table>

<h2>2. Admin Frontend and Backend Test Register</h2>
<table class='small'><tr><th style='width:7%'>Test ID</th><th style='width:7%'>Layer</th><th style='width:11%'>Feature / Component</th><th style='width:16%'>Test Action</th><th style='width:17%'>Expected Result</th><th style='width:15%'>Failure Condition</th><th style='width:10%'>Actual Result</th><th style='width:5%'>P/F</th><th style='width:12%'>Evidence Ref.</th></tr>
$(($tests | ForEach-Object { Row $_ }) -join "`n")</table>

<h2>3. Failure and Retest Register</h2>
<table><tr><th style='width:8%'>Test ID</th><th style='width:16%'>Failure Observed</th><th style='width:12%'>Error / Log</th><th style='width:16%'>Root Cause</th><th style='width:18%'>Corrective Action</th><th style='width:12%'>Retest Result</th><th style='width:10%'>Evidence</th><th style='width:8%'>Closed?</th></tr>
$(1..12 | ForEach-Object { '<tr><td>&nbsp;</td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>' })</table>

<h2>4. Required Evidence Checklist</h2>
<p class='check'>Use short, named evidence files. Recommended naming: <b>ADM-BE-01_LoginServlet_Pass.png</b>.</p>
<table><tr><th>Evidence Group</th><th>Required proof</th><th>Suggested file / clip</th></tr>
<tr><td>Infrastructure</td><td>Tomcat startup, /uninest deployment, Database connection established, SHOW TABLES result</td><td>01_Infrastructure.mp4 (30-45 seconds)</td></tr>
<tr><td>Authentication</td><td>Valid login, invalid login, session redirect, logout</td><td>02_Authentication.mp4</td></tr>
<tr><td>Listings</td><td>Queue, search/filter, approve/decline, SQL row before and after</td><td>03_Listings.mp4</td></tr>
<tr><td>Operations</td><td>Booking, enquiry/message, maintenance and notification actions</td><td>04_Operations.mp4</td></tr>
<tr><td>Account / Records</td><td>Profile update, records/payment status, failure response</td><td>05_Account_Records.mp4</td></tr></table>

<h2>5. Backend Code Evidence</h2>
<p>Each excerpt is generated from the current source tree. It links the servlet mapping and its principal request/database operation to the matching test IDs. These focused excerpts are more legible than full-file screenshots.</p>
"@

$map = @{
'LoginServlet.java'='ADM-BE-01 to ADM-BE-03';'LogoutServlet.java'='ADM-BE-23';'ForgotPasswordServlet.java'='ADM-BE-19';'AdminRegisterRequestServlet.java'='ADM-BE-20';'RegisterServlet.java'='Related registration validation';'ProfileServlet.java'='ADM-BE-18';'ApproveBookingServlet.java'='ADM-BE-10';'BookingActionServlet.java'='ADM-BE-11';'EnquiriesServlet.java'='ADM-BE-12';'MaintenanceServlet.java'='ADM-BE-15';'MessagesServlet.java'='ADM-BE-14';'SendMessageServlet.java'='ADM-BE-13';'NotificationServlet.java'='ADM-BE-17';'AdminPageServlet.java'='ADM-BE-04';'PaymentsServlet.java'='ADM-BE-16';'AddPropertyServlet.java'='Listing navigation';'AdminListingQueueServlet.java'='ADM-BE-05 and ADM-BE-06';'EditPropertyServlet.java'='ADM-BE-09';'VerifyPropertyServlet.java'='ADM-BE-07 and ADM-BE-08';'DatabaseStatusServlet.java'='ADM-BE-21'
}

$files=Get-ChildItem -LiteralPath $servletRoot -File -Recurse -Filter '*.java' | Sort-Object Name
$fig=1
foreach($f in $files){
  $lines=Get-Content -LiteralPath $f.FullName
  $keep=for($i=0;$i -lt $lines.Count;$i++){ if($lines[$i] -match '@WebServlet|doGet|doPost|prepareStatement|executeQuery|executeUpdate|sendRedirect|setStatus|sendError'){ [Math]::Max(0,$i-1)..[Math]::Min($lines.Count-1,$i+2) } }
  $keep=@($keep | Sort-Object -Unique | Select-Object -First 34)
  $excerpt=($keep | ForEach-Object { '{0,4}: {1}' -f ($_+1),$lines[$_] }) -join "`r`n"
  $test=$map[$f.Name]; if(-not $test){$test='Supporting Admin backend'}
  $html += "<h3>5.$fig $(HtmlEncode $f.BaseName) - $(HtmlEncode $test)</h3><p class='fig'>Figure $fig. Source evidence: $(HtmlEncode ($f.FullName.Substring($root.Length+1)))</p><div class='code'>$(HtmlEncode $excerpt)</div>"
  $fig++
}

$html += @"
<h2>6. Video Recording Plan and File Size</h2>
<ol><li>Record at <b>1280 x 720, 30 FPS</b>. Do not record 1080p unless required.</li><li>In OBS Output settings use <b>MKV</b>, encoder H.264, rate control CBR, video bitrate <b>1500-2200 Kbps</b>, audio 96 Kbps.</li><li>Record five short clips using the evidence groups above. Pause between tests; do not leave build waiting time in the recording.</li><li>Show the test ID on screen or say it aloud before each action. Show frontend result, NetBeans/Tomcat result, then the relevant MySQL row.</li><li>After recording, use OBS <b>File - Remux Recordings</b> to create MP4 if the lecturer requires MP4.</li><li>If only one upload is allowed, combine the short clips in order. Keep the original clips until marking is complete.</li></ol>
<div class='callout'><b>Estimated size:</b> At 1800 Kbps video plus 96 Kbps audio, a 10-minute recording is roughly 142 MB. A 22 MB infrastructure clip is normal. Cutting idle build time and recording at 720p reduces size more effectively than making text unreadable.</div>

<h2>7. Final Submission Checklist</h2>
<p class='check'>☐ All Actual Result cells completed<br>☐ Every test marked Pass or Fail<br>☐ Every failure entered in the Failure and Retest Register<br>☐ Database before/after evidence captured for write operations<br>☐ No passwords visible in screenshots or video<br>☐ Video test IDs match the report<br>☐ Final document and clips open successfully before submission</p>
</body></html>
"@

[System.IO.File]::WriteAllText($out,$html,[System.Text.UTF8Encoding]::new($true))
Get-Item -LiteralPath $out
