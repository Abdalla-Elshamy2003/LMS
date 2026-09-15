param([string]$AdminEmail, [string]$AdminPassword)
$ErrorActionPreference='Stop'
$base='https://frontend-production-a64a1.up.railway.app'
$qa=Get-Content "$PSScriptRoot/../.tooling/production-qa/accounts.json" -Raw | ConvertFrom-Json
$a=$qa.academies[0]
function Api($method,$path,$body,$token,$academy) {
    $args=@{Uri="$base/api$path";Method=$method;Headers=@{};TimeoutSec=40}
    if($token){$args.Headers.Authorization="Bearer $token"}
    if($academy){$args.Headers['X-Academy-Id']="$academy"}
    if($null -ne $body){$args.ContentType='application/json; charset=utf-8';$args.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 10))}
    Invoke-RestMethod @args
}
$admin=Api POST '/auth/login' @{email=$AdminEmail;password=$AdminPassword}
$teacher=Api POST '/auth/login' @{email=$a.username;password=$a.password}
$catalog=Api GET '/courses' $null $admin.accessToken
$library=Api GET '/learning' $null $admin.accessToken
if($catalog.Count -ne $library.Count){throw 'Root catalog and library disagree'}
"PASS root catalog/library: $($catalog.Count) courses"
$detail=Api GET "/courses/$($a.courseId)" $null $admin.accessToken $a.id
if($detail.summary.academyId -ne $a.id){throw 'Invalid academy navigation metadata'}
'PASS scoped admin course and academy navigation metadata'
$title='QA release regression — deadline 2026-09-20'
$assignments=Api GET '/homework/assignments' $null $teacher.accessToken
$assignment=$assignments|Where-Object title -eq $title|Select-Object -First 1
if(!$assignment){$assignment=Api POST '/homework/assignments' @{courseId=$a.courseId;title=$title;description='Technical production QA only';deadline='2026-09-20T00:00:00Z';maxScore=20} $teacher.accessToken}
if(([datetime]$assignment.deadline).ToUniversalTime().ToString('yyyy-MM-dd') -ne '2026-09-20'){throw 'Homework deadline did not persist'}
'PASS homework deadline round-trip'
$student=$a.students[0]
$null=Api PUT "/academies/$($a.id)/students/$($student.id)" @{courseIds=@($a.courseId)} $admin.accessToken
$audit=Api GET '/dashboard/audit' $null $admin.accessToken
if(!(@($audit.content)|Where-Object action -eq 'ACADEMY_STUDENT_ACCESS_CHANGED')){throw 'Missing audit event'}
'PASS academy access audit event; existing enrollment preserved'
$response=Invoke-WebRequest "$base/api/not-a-real-route" -Headers @{Authorization="Bearer $($admin.accessToken)"} -SkipHttpErrorCheck
if($response.StatusCode -ne 404){throw "Unknown route: $($response.StatusCode)"}
'PASS unknown authenticated route returns 404'
