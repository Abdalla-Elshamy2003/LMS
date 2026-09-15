$ErrorActionPreference='Stop'
$qa=Get-Content "$PSScriptRoot/../.tooling/production-qa/accounts.json" -Raw | ConvertFrom-Json
$base=$qa.base; $a=$qa.academies[0]; $s=$a.students[0]
function Login($account) { Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType application/json -Body (@{email=$account.username;password=$account.password}|ConvertTo-Json) }
$t=Login $a; $student=Login $s; $other=Login $qa.academies[1]
$script:results=@()
function Check($name,$path,$token,$expected,$headers=@{},$method='GET') {
    if($token){$headers.Authorization="Bearer $token"}
    $r=Invoke-WebRequest "$base$path" -Method $method -Headers $headers -SkipHttpErrorCheck -TimeoutSec 30
    $row=@{test=$name;status=[int]$r.StatusCode;expected=$expected;pass=([int]$r.StatusCode -in $expected)}
    $script:results+=,$row; Write-Output ($row|ConvertTo-Json -Compress)
}
Check 'student-own-course' "/api/courses/$($a.courseId)" $student.accessToken @(200)
Check 'student-classmate-denied' '/api/students/33' $student.accessToken @(403,404)
Check 'student-other-academy-denied' '/api/courses/54' $student.accessToken @(403,404)
Check 'student-cannot-spoof-academy' '/api/courses/54' $student.accessToken @(401,403,404) @{'X-Academy-Id'='3'}
Check 'student-cannot-list-staff' '/api/users' $student.accessToken @(403)
Check 'student-cannot-read-audit' '/api/dashboard/audit' $student.accessToken @(403)
Check 'student-cannot-read-question-bank' '/api/exams/questions' $student.accessToken @(403)
Check 'student-cannot-see-learners' '/api/learning/courses/53/learners' $student.accessToken @(403)
Check 'student-exams-functional-regression' '/api/exams' $student.accessToken @(200)
Check 'anonymous-learning-denied' '/api/learning' $null @(401)
Check 'anonymous-files-denied' '/api/files/t3/materials/qa.png' $null @(401)
Check 'url-query-token-rejected' '/api/files/t3/materials/qa.png?token=invalid' $null @(401)
Check 'forged-bearer-denied' '/api/learning' 'invalid.jwt.value' @(401)
Check 'untrusted-cors-origin' '/api/learning' $null @(403) @{Origin='https://qa-untrusted.invalid';'Access-Control-Request-Method'='GET'} 'OPTIONS'
Check 'student-private-other-tenant-file-denied' "/api/files/t$($qa.academies[1].tenantId)/materials/qa.png" $student.accessToken @(403,404)
$headers=@{Authorization="Bearer $($t.accessToken)"}
$bad=Invoke-WebRequest "$base/api/files/upload" -Method Post -Headers $headers -Form @{folder='materials';file=Get-Item "$PSScriptRoot/../frontend/public/images/teacher-placeholder.svg"} -SkipHttpErrorCheck
$row=@{test='svg-upload-rejected';status=[int]$bad.StatusCode;pass=([int]$bad.StatusCode -eq 400)}; $results+=,$row; $row|ConvertTo-Json -Compress
$blocked=Invoke-WebRequest "$base/api/files/upload" -Method Post -Headers @{Authorization="Bearer $($student.accessToken)"} -Form @{folder='materials';file=Get-Item "$PSScriptRoot/../frontend/public/images/logo.png"} -SkipHttpErrorCheck
$row=@{test='student-course-upload-rejected';status=[int]$blocked.StatusCode;pass=([int]$blocked.StatusCode -eq 403)}; $results+=,$row; $row|ConvertTo-Json -Compress
if (Test-Path "$PSScriptRoot/../.tooling/production-qa/security-results.json") {
    $previous=Get-Content "$PSScriptRoot/../.tooling/production-qa/security-results.json" -Raw|ConvertFrom-Json
    $file=@{fileKey=$previous.fileKey}; $material=@{id=$previous.materialId}
} else {
$file=Invoke-RestMethod "$base/api/files/upload" -Method Post -Headers $headers -Form @{folder='materials';file=Get-Item "$PSScriptRoot/../frontend/public/images/logo.png"}
$body=@{type='IMAGE';title='QA ملف صورة لاختبار الوصول — شعار المنصة';description='ملف اختبار تقني.';fileKey=$file.fileKey;sizeBytes=$file.size}|ConvertTo-Json
$material=Invoke-RestMethod "$base/api/courses/lessons/$($a.lessonId)/materials" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
}
Check 'enrolled-student-downloads-file' "/api/files/$($file.fileKey)" $student.accessToken @(200)
Check 'other-teacher-file-denied' "/api/files/$($file.fileKey)" $other.accessToken @(403,404)
Check 'anonymous-real-file-denied' "/api/files/$($file.fileKey)" $null @(401)
@{results=$results;fileKey=$file.fileKey;materialId=$material.id;date=(Get-Date).ToString('o')}|ConvertTo-Json -Depth 8|Set-Content "$PSScriptRoot/../.tooling/production-qa/security-results.json" -Encoding utf8
"CHECKS=$($results.Count) PASSED=$(@($results|Where-Object pass).Count)"
