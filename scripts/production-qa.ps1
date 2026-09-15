param([string]$AdminEmail, [string]$AdminPassword, [switch]$VerifyOnly)
$ErrorActionPreference = 'Stop'
$base = 'https://frontend-production-a64a1.up.railway.app'
$run = 'qa-20260912'
$privateDir = Join-Path $PSScriptRoot '../.tooling/production-qa'
[void](New-Item -ItemType Directory -Force -Path $privateDir)
$manifestPath = Join-Path $privateDir 'accounts.json'
function Save-Manifest { $script:manifest | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $manifestPath -Encoding utf8 }
function New-Password {
    $bytes = New-Object byte[] 24
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    'Qa9!' + [Convert]::ToBase64String($bytes)
}
function Api($method, $path, $body, $token) {
    $args = @{Uri="$base/api$path"; Method=$method; TimeoutSec=40; Headers=@{} }
    if ($token) { $args.Headers.Authorization = "Bearer $token" }
    if ($null -ne $body) { $args.ContentType='application/json; charset=utf-8'; $args.Body=[Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 15 -Compress)) }
    Invoke-RestMethod @args
}
if (Test-Path -LiteralPath $manifestPath) { $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json -AsHashtable }
else {
    $manifest = @{base=$base; run=$run; academies=@()}
    $subjects = @('الرياضيات','الفيزياء','الكيمياء','اللغة العربية','الأحياء')
    for ($i=1; $i -le 5; $i++) {
        $manifest.academies += @{index=$i; name="QA اختبار مدرس $i — $($subjects[$i-1])"; subject=$subjects[$i-1]; slug="$run-teacher-$i"; username="$run-teacher-$i"; password=(New-Password); students=@()}
    }
    Save-Manifest
}
$admin = Api POST '/auth/login' @{email=$AdminEmail;password=$AdminPassword} $null
if ($admin.user.role -ne 'SUPER_ADMIN') { throw 'Root admin required' }
if (!$VerifyOnly) {
    foreach ($a in $manifest.academies) {
        if (!$a.id) {
            $existing = @(Api GET '/academies' $null $admin.accessToken) | Where-Object slug -eq $a.slug
            if ($existing) { $academy=$existing } else { $academy=Api POST '/academies' @{name=$a.name;slug=$a.slug;username=$a.username;password=$a.password} $admin.accessToken }
            $a.id=$academy.id; $a.tenantId=$academy.tenantId; Save-Manifest
        }
        if (!$a.configured) {
            $null = Api PUT "/academies/$($a.id)" @{name=$a.name;tagline='بيانات اختبار QA — ليست خدمة تعليمية للبيع';headline="مساحة اختبار $($a.subject)";description='بيانات تجريبية لاختبار الإنتاج فقط. لا تسدد أي مبالغ لهذه الكورسات.';aboutText='هذه مساحة QA معزولة لفحص صلاحيات المدرس والطالب وتشغيل المحتوى. الفيديو مجرد عينة تقنية قصيرة وليس شرحاً تعليمياً.';subject=$a.subject;phone='';demoContent=$false;published=$true;videos=@()} $admin.accessToken
            $a.configured=$true; Save-Manifest
        }
        $teacher=Api POST '/auth/login' @{email=$a.username;password=$a.password} $null
        if (!$a.courseId) {
            $existing=@(Api GET '/courses' $null $teacher.accessToken) | Where-Object title -eq "QA — أساسيات $($a.subject) — اختبار فقط"
            if ($existing) { $a.courseId=$existing[0].id } else {
                $c=Api POST '/courses' @{title="QA — أساسيات $($a.subject) — اختبار فقط";subject=$a.subject;gradeLevel='المرحلة الثانوية';grade='الصف الأول الثانوي';description='كورس تجريبي لاختبار المنصة. ليس محتوى تعليمياً للبيع.';price=0;schedule='تجربة QA بدون مواعيد فعلية'} $teacher.accessToken
                $a.courseId=$c.summary.id
            }
            Save-Manifest
        }
        if (!$a.moduleId) { $m=Api POST "/courses/$($a.courseId)/modules" @{title='الوحدة التجريبية الأولى';position=1} $teacher.accessToken; $a.moduleId=$m.id; Save-Manifest }
        if (!$a.lessonId) { $l=Api POST "/courses/modules/$($a.moduleId)/lessons" @{title='معاينة المحتوى قبل تشغيل الفيديو';position=1;durationMin=1;contentText="قبل المشاهدة: ستتعرف على ترتيب درس $($a.subject)، وصف المحتوى، ومصادر التعلم. الفيديو عينة تقنية قصيرة لزهرة وليس شرحاً تعليمياً."} $teacher.accessToken; $a.lessonId=$l.id; Save-Manifest }
        if (!$a.materialId) { $m=Api POST "/courses/lessons/$($a.lessonId)/materials" @{type='VIDEO';title='عينة تقنية لاختبار الفيديو — ليست محاضرة';description='فيديو CC0 قصير لاختبار المعاينة والتشغيل فقط.';url='https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4';durationSec=5} $teacher.accessToken; $a.materialId=$m.id; Save-Manifest }
        for ($j=1; $j -le 2; $j++) {
            $s=$a.students | Where-Object index -eq $j
            if (!$s) { $s=@{index=$j;username="$run-student-$($a.index)-$j";password=(New-Password);name="QA طالب $($a.index)-$j"}; $a.students+=,$s; Save-Manifest }
            if (!$s.id) { $created=Api POST "/academies/$($a.id)/students" @{fullName=$s.name;username=$s.username;password=$s.password;courseIds=@($a.courseId)} $admin.accessToken; $s.id=$created.id; Save-Manifest }
        }
        Write-Output "PROVISIONED academy=$($a.id) course=$($a.courseId) lesson=$($a.lessonId) students=$($a.students.Count)"
    }
}
$results=@()
foreach ($a in $manifest.academies) {
    $teacher=Api POST '/auth/login' @{email=$a.username;password=$a.password} $null
    $courses=@(Api GET '/courses' $null $teacher.accessToken)
    $results+=@{test="teacher-$($a.index)-own-course-only";pass=($courses.Count -eq 1 -and $courses[0].id -eq $a.courseId)}
    foreach ($s in $a.students) {
        $student=Api POST '/auth/login' @{email=$s.username;password=$s.password} $null
        $library=@(Api GET '/learning' $null $student.accessToken)
        $results+=@{test="student-$($a.index)-$($s.index)-library";pass=($library.Count -eq 1)}
    }
    $other=$manifest.academies | Where-Object index -ne $a.index | Select-Object -First 1
    try { $null=Api GET "/courses/$($other.courseId)" $null $teacher.accessToken; $code=200 } catch { $code=[int]$_.Exception.Response.StatusCode }
    $results+=@{test="teacher-$($a.index)-cross-tenant-denied";status=$code;pass=($code -in 403,404)}
}
$results | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $privateDir 'api-results.json') -Encoding utf8
$results | ForEach-Object { "CHECK $($_.test) pass=$($_.pass) status=$($_.status)" }
Write-Output "Private credentials saved outside deployment context: $manifestPath"
