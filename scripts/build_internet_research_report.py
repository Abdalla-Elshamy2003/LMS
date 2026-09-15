import sys, os, math
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / '.tooling/pdf-libs'))
import arabic_reshaper
from bidi.algorithm import get_display
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.colors import HexColor, Color, white
from reportlab.lib.pagesizes import A4
from pypdf import PdfReader

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/pdf/Egypt_Education_Platforms_Internet_Research_2026-09-12.pdf'
OUT.parent.mkdir(parents=True,exist_ok=True)
pdfmetrics.registerFont(TTFont('Arabic','C:/Windows/Fonts/arial.ttf'))
pdfmetrics.registerFont(TTFont('ArabicBold','C:/Windows/Fonts/arialbd.ttf'))
W,H=A4; M=43; CW=W-2*M
INK=HexColor('#12283B'); TEAL=HexColor('#087E83'); GOLD=HexColor('#DAA44B'); MUTED=HexColor('#546777'); BG=HexColor('#F1F5F8'); RED=HexColor('#AE4436')
c=canvas.Canvas(str(OUT),pagesize=A4)
c.setTitle('نتائج البحث: أسعار اشتراكات المنصات التعليمية في مصر')
c.setAuthor('Market Research')
c.setSubject('Published educational platform subscription pricing, 12 September 2026')
page=0; y=0

def shape(s): return get_display(arabic_reshaper.reshape(str(s)),base_dir='R')
def lines(s,width,font='Arabic',size=11):
    result=[]
    for part in str(s).split('\n'):
        line=''
        for word in part.split():
            candidate=(line+' '+word).strip()
            if line and pdfmetrics.stringWidth(shape(candidate),font,size)>width:
                result.append(line); line=word
            else: line=candidate
        result.append(line)
    return result
def text(s,x,yy,size=11,font='Arabic',color=INK,align='right'):
    c.setFillColor(color); c.setFont(font,size)
    if align=='right': c.drawRightString(x,yy,shape(s))
    else: c.drawString(x,yy,str(s))
def para(s,size=11.8,color=INK,gap=10,bold=False):
    global y
    for line in lines(s,CW,'ArabicBold' if bold else 'Arabic',size):
        text(line,W-M,y,size,'ArabicBold' if bold else 'Arabic',color); y-=size*1.6
    y-=gap
    assert y>40, f'Page {page} overflow: {y}'
def sub(s):
    global y
    y-=6; text(s,W-M,y,15,'ArabicBold',TEAL); y-=27
def note(s): para(s,10.1,MUTED,gap=8)
def start(title,kicker='EGYPT / MARKET RESEARCH'):
    global page,y
    if page:c.showPage()
    page+=1
    c.setFillColor(INK);c.rect(0,H-18,W,18,fill=1,stroke=0)
    text(kicker,M,H-43,8,'ArabicBold',TEAL,align='left')
    text('١٢ سبتمبر ٢٠٢٦',W-M,H-43,9,'Arabic',MUTED)
    text(title,W-M,H-82,23,'ArabicBold');
    c.setStrokeColor(GOLD);c.setLineWidth(2);c.line(W-M-65,H-99,W-M,H-99)
    c.setStrokeColor(HexColor('#D9E1E7'));c.setLineWidth(.5);c.line(M,38,W-M,38)
    text('بحث سوقي | أسعار معلنة وليست عروضًا تعاقدية',W-M,24,8,'Arabic',MUTED)
    text(f'{page:02}',M,24,9,'ArabicBold',TEAL,align='left');y=H-127
def table(headers,rows,widths,size=10):
    global y
    # First logical column appears at the right side of the Arabic table.
    assert abs(sum(widths)-CW)<1
    allrows=[headers]+rows
    for i,row in enumerate(allrows):
        wrapped=[lines(cell,widths[j]-15,'ArabicBold' if i==0 else 'Arabic',size) for j,cell in enumerate(row)]
        rh=max(len(a) for a in wrapped)*(size*1.42)+17
        assert y-rh>52,f'Table overflow page {page}: {y-rh}'
        c.setFillColor(TEAL if i==0 else (BG if i%2 else white)); c.rect(M,y-rh,CW,rh,fill=1,stroke=0)
        right=W-M
        for j,ls in enumerate(wrapped):
            for k,line in enumerate(ls): text(line,right-7,y-12-size-k*size*1.42,size,'ArabicBold' if i==0 else 'Arabic',white if i==0 else INK)
            right-=widths[j]
        y-=rh
    y-=17
def source(n,label,url):
    global y
    text(f'[{n}] {label}',W-M,y,10,'ArabicBold',TEAL)
    c.linkURL(url,(M,y-3,W-M,y+12),relative=0,thickness=0)
    y-=16
    text(url,M,y,8.1,'Arabic',MUTED,align='left');c.linkURL(url,(M,y-3,W-M,y+10),relative=0,thickness=0);y-=26

# 1 / Cover
start('اشتراكات المنصات التعليمية في مصر','EGYPT / INTERNET RESEARCH')
c.setFillColor(INK);c.roundRect(M,H-415,CW,272,16,fill=1,stroke=0)
text('نتائج البحث',W-M-25,H-211,38,'ArabicBold',white)
text('الأسعار والسعات المنشورة على الإنترنت',W-M-25,H-254,19,'ArabicBold',HexColor('#83DDDA'))
text('مقارنة اشتراكات منصات التعليم في مصر',W-M-25,H-293,17,'Arabic',white)
text('شهريًا وسنويًا | المدرسون | الطلاب | الرسوم الإضافية',W-M-25,H-330,12,'Arabic',white)
text('مصادر رسمية وروابط مباشرة',W-M-25,H-382,12,'Arabic',HexColor('#D6E3EB'))
y=H-452
table(['منصات مرجعية','تسعير بالجنيه','مصادر إلكترونية'],[['10 منصات','9 منصات','11 صفحة رسمية']], [CW/3]*3,12)
para('الاشتراكات الشهرية والسنوية، وحدود المدرسين والطلاب، والرسوم الإضافية كما نشرها الموردون، مع تمييز البيانات غير المعلنة والمتعارضة.',13,TEAL,bold=True)
note('تاريخ البحث: 12 سبتمبر 2026. الأسعار لقطة من المواقع الرسمية وقت البحث، وقد تتغير عند التعاقد. هذا ليس حصرًا لكل منصة في مصر أو شهادة اعتماد أمني للمنافسين.')

# 2 / Scope
start('الخلاصة التنفيذية ونطاق البحث')
sub('ما الذي تقارنه هذه الدراسة؟')
para('اشتراك البرنامج الذي يدفعه المدرس أو السنتر لإدارة نشاطه، وليس سعر الحصة أو الكورس الذي يدفعه الطالب. سعة الطلاب تعني حد الحسابات أو الطلاب النشطين بحسب تعريف كل مورد، وليست عدد طلاب يضمن المورد جلبهم لك.')
table(['المجموعة','المنصات المشمولة'],[
 ['إدارة سنتر وحضور وتحصيل','Hessity، TeachWise'],
 ['محتوى وكورسات أو نظام مختلط','سهولة، دروسنا، طفرة تك، YallaTeach، انتماء، رِواق أكاديمي، دارسك AI'],
 ['مرجع إقليمي يحتاج عرضًا مصريًا','Classera؛ بيانات الصفحة غير كافية لاعتماد سعر نهائي']
],[145,CW-145],11)
sub('أهم ما ينبغي أخذه من الأرقام')
para('تبدأ إحدى الباقات المدفوعة المنشورة في العينة عند 99 جنيهًا شهريًا، بينما توجد باقات تصل إلى 8,000 جنيه مع سعات وخدمات مختلفة. هذا نطاق العينة، لا متوسط السوق ولا دليل تكافؤ في جودة الفيديو أو الأمان. [1، 5]')
para('حد المدرسين منشور بوضوح في بعض الباقات فقط. وجود مشرفين أو مساعدين لا يعني تلقائيًا وجود مساحات مستقلة لكل مدرس. لذلك نعرض «غير معلن» بدل استنتاج سعة غير موثقة.')
sub('منهج التحقق')
note('المصدر هو صفحة المورد العامة، لا إعلان وسيط أو منشور اجتماعي. لم يتم شراء اشتراك أو التفاوض أو تدقيق الشركات. الأسعار لا تُعتبر شاملة للضرائب أو عمولة الدفع أو الفيديو إلا بنص واضح. لا يتضمن التقرير أسعارًا مقترحة أو حسابات ربحية افتراضية.')

# 5 / First matrix
start('أسعار منشورة بالجنيه: المجموعة الأولى')
note('السعر إجمالي الباقة شهريًا، لا لكل طالب. «غ.م» = حد حسابات المدرسين غير معلن رقميًا. الأعداد من صفحات الموردين [1-5].')
table(['المنصة / الباقة','جنيه / شهر','الطلاب','المدرسون'],[
 ['سهولة: مجانية','0','50 حسابًا','غ.م'],
 ['سهولة: بداية','99','200','غ.م'],
 ['سهولة: نمو','199','1,000','غ.م'],
 ['سهولة: احترافية','499','3,000','غ.م'],
 ['سهولة: مؤسسات','999','غير محدود*','غ.م'],
 ['Hessity: مجانية معلنة*','0*','50*','غ.م'],
 ['Hessity: حتى 200','699','200','غ.م'],
 ['Hessity: حتى 500','1,499','500','غ.م'],
 ['Hessity: حتى 1,000','2,999','1,000','غ.م'],
 ['TeachWise: شهرية','500 عرض','2,000','غ.م'],
 ['YallaTeach: مستقل','450','300','1'],
 ['YallaTeach: احترافية','850','1,000','3'],
 ['YallaTeach: شاملة','1,600','3,000','5'],
 ['طفرة تك: البداية','999 عرض','100','1'],
 ['طفرة تك: النمو','2,500','300','3'],
 ['طفرة تك: المحترف','5,000','600','5'],
 ['طفرة تك: الأعمال','8,000','1,000 نشط','10']
],[213,95,105,CW-413],9.5)
note('* Hessity: الشروط تناقض المجانية وتعرّف السعة كتسجيلات في المجموعات؛ راجع صفحة 6. «غير محدود» وصف المورد. طفرة تك: عرض بدل 1,500. TeachWise: عرض بدل 1,000؛ التخفيضات غير مضمونة الاستمرار.')

# 6 / Second matrix
start('أسعار منشورة بالجنيه: المجموعة الثانية')
note('السعر إجمالي شهري. «غ.م» = عدد المدرسين غير معلن؛ لا يُستبدل بعدد المشرفين. المصادر [6-9].')
table(['المنصة / الباقة','جنيه / شهر','الطلاب','المدرسون'],[
 ['دروسنا: المبتدئ','500','100','غ.م'],
 ['دروسنا: المحترف','1,500','500','غ.م'],
 ['دروسنا: السنتر','4,000','2,000','غ.م'],
 ['انتماء: الأساسية','900','500','غ.م'],
 ['انتماء: النمو','1,800','2,000','غ.م'],
 ['انتماء: الاحترافية','2,800','6,000','غ.م'],
 ['رِواق: تجربة 7 أيام','0','50','غ.م'],
 ['رِواق: الأساسية','499','150 نشط','غ.م'],
 ['رِواق: PRO','999','500 نشط','غ.م'],
 ['رِواق: ULTRA','1,500','1,000 نشط','غ.م'],
 ['دارسك AI: Starter','499','200','غ.م'],
 ['دارسك AI: Pro','1,199','1,000','غ.م'],
 ['دارسك AI: Enterprise','2,999','غير محدود*','غ.م']
],[213,95,105,CW-413],10)
sub('فروق في سعة المحتوى')
note('دروسنا: 1 / 5 / 20 كورسًا. رِواق المدفوعة: 3 / 10 / 20 كورسًا. دارسك: 20 / 50 / غير محدود، وتخزين 10 / 50 / 200 جيجابايت. لا يكفي حد الطلاب وحده لاختيار الباقة.')
note('انتماء تعلن مشرفين بلا حد، لكن ذلك لا يثبت عدد حسابات مدرس مستقلة. رِواق تعلن مساعدين دون حد رقمي منشور. المقصود هنا riwaq-academy.org وليس منصة رواق التعليمية ذات الاسم المشابه.')

# 7 / Annual
start('الفوترة السنوية والشراء الكامل')
note('الجدول يعرض المعلن على المواقع فقط؛ لا نحول نسب الخصم إلى أسعار سنوية غير منشورة. الدفعة السنوية ليست عقدًا شهريًا مرنًا.')
table(['المنصة','المعلن للدفع السنوي','السعة أو التحفظ'],[
 ['سهولة [1]','خصم 20%؛ يُدفع مقدمًا','السعات حسب الباقة'],
 ['Hessity [2]','توفير حتى 33%','لا سعر تفصيلي مؤكد هنا؛ راجع تعارض الشروط'],
 ['TeachWise [3]','4,500 جنيه سنويًا، عرض بدل 9,000','حتى 4,000 طالب؛ ليست سعة الشهرية نفسها'],
 ['طفرة تك [5]','توفير 17%','قيمة الفاتورة النهائية حسب الباقة'],
 ['دروسنا [6]','سنوي 15%؛ نصف سنوي 10%؛ ربع سنوي 5%','الخصومات معلنة؛ لا أرقام نهائية محسوبة هنا'],
 ['رِواق [8]','شهران مجانًا عند الاشتراك السنوي','حسب الباقة المختارة']
],[110,205,CW-315],10.1)
sub('الشراء الكامل ليس اشتراكًا مدى الحياة بلا مصروفات')
table(['انتماء','سعر الشراء','تشغيل سنوي لاحق','سعة الشراء'],[
 ['أساسية','17,000','1,000','3,000 طالب'],
 ['نمو','24,000','1,500','8,000 طالب'],
 ['احترافية','38,800','2,400','25,000 طالب']
],[92,110,150,CW-352],10.3)
note('هذه سعات الشراء لا الاشتراك الشهري. اطلب توضيح ملكية الكود، حق نقل الاستضافة، وبداية رسوم التشغيل. التطبيق الاختياري معروض بـ3,500 دون دورة فوترة واضحة؛ يلزم تأكيدها. [7]')

# 8 / Fees and source uncertainty
start('التكلفة الحقيقية وجودة بيانات التسعير')
table(['البند','ما وجده البحث','ما يجب تأكيده'],[
 ['سهولة [1]','شراء النطاق وخدمات البث الخارجية منفصلة','تكلفة الفيديو والمشاهدة، لا مساحة الملفات فقط'],
 ['Hessity [2، 11]','تدقيق 249 وتقارير متقدمة 499 شهريًا','تعارض السعة والمجانية؛ الشروط تنفي عمولة النسبة'],
 ['دارسك AI [9]','عمولة موصوفة: 3% شهري / 4% فوري','أساس العمولة ومعنى التسوية وهل البوابة منفصلة'],
 ['رِواق [8]','تعلن صفر عمولة منصة واعتماد إيصالات','التحويل اليدوي ليس بوابة دفع آلية موثقة'],
 ['YallaTeach [4]','سنتر: 750 / 1,500 / 3,000؛ حدود غير ظاهرة','هوية المورد والتشغيل؛ رقم عام يبدو تجريبيًا'],
 ['كل العينة','إعلان «آمن» أو «محمي» ليس تدقيقًا','العزل، النسخ، تصدير البيانات، والتزام الخدمة']
],[95,208,CW-303],10.5)
sub('Classera: مرجع لا يدخل ترتيب الأرخص')
para('تعرض الصفحة الرسمية Plus بسعر 75 دولارًا شهريًا أو 900 سنويًا، وPro بسعر 112 شهريًا أو 1,350 سنويًا، وPremium بسعر 150 شهريًا أو 1,800 سنويًا. خانة الطلاب تبدأ عند 50، لكن ربط السعر بهذا الحد غير واضح، وعدد المدرسين غير محدد. [10]',11.2)
note('تظهر رموز قالب غير معالجة ومحتويات نموذجية في الصفحة. لذلك لا نعتمد هذه الأرقام كعرض صالح لمصر، ولا نحولها إلى جنيه أو نحتسب تكلفة طالب منها. المطلوب عرض مكتوب يحدد العملة والسعات والضرائب والتفعيل.')
sub('تعارض مهم في شروط Hessity')
note('الشروط تنفي وجود مجانية دائمة وتذكر تجربة 30 يومًا قابلة للتغيير، خلاف واجهة التسويق. السعة فيها تسجيلات نشطة: طالب في مجموعتين يُحسب مرتين؛ والطالب وولي الأمر لا يدفعان اشتراك البرنامج. لا تعتمد أرقام صفحة 3 كحد طلاب فريدين قبل تأكيد مكتوب. [11]')

# 12 / References
start('المصادر وحدود الاعتماد','EGYPT / SOURCE REGISTER')
note('تاريخ الاطلاع لجميع صفحات السوق: 12 سبتمبر 2026. الروابط قابلة للنقر. أرقام المصادر داخل الجداول تشير إلى هذه القائمة. الخصائص والأسعار ادعاءات منشورة من المورد وليست تدقيقًا مستقلًا لخدمته.')
for item in [
 (1,'سهولة: صفحة الخطط والأسعار','https://soohola.com/pricing/'),
 (2,'Hessity: الخطط والإضافات','https://www.hessity.com/ar'),
 (3,'TeachWise: الاشتراك الشهري والسنوي','https://teachwise.org/'),
 (4,'YallaTeach: باقات المدرسين والسناتر','https://www.centeryalla.online/'),
 (5,'طفرة تك: باقات حسب المدرسين والطلاب','https://tafra-tech.com/'),
 (6,'دروسنا: الخطط ودورات الفوترة','https://dorosna.online/pricing'),
 (7,'انتماء: اشتراك وشراء كامل','https://intmaa.com/'),
 (8,'رِواق أكاديمي: الباقات وإيصالات التحويل','https://riwaq-academy.org/'),
 (9,'دارسك AI: الباقات والعمولة المعلنة','https://darsakai.com/'),
 (10,'Classera: صفحة تسعير تستلزم تأكيدًا','https://classera.com/en/company/pricing/'),
 (11,'Hessity: شروط الاستخدام وتعريف السعة','https://www.hessity.com/ar/terms')
]: source(*item)
note('تعتمد هذه النسخة على نتائج البحث الإلكتروني فقط. لم يتم شراء اشتراكات أو الحصول على عروض تعاقدية مباشرة من الموردين.')
note('حاول البحث التحقق من مزودين إضافيين، منهم NazzemEdu وTrackademi، لكن صفحاتهم لم تُفتح بصورة يمكن الاعتماد عليها؛ لذلك لم تُنسخ أسعار غير متحققة ولم تدخل المقارنة. استكمال السوق يحتاج عروضًا مباشرة وقائمة موردين إضافية عند الحاجة.')
c.save()
r=PdfReader(str(OUT))
assert len(r.pages)==7,len(r.pages)
assert all(len(p.extract_text() or '')>100 for p in r.pages)
print(f'Created {OUT} | pages={len(r.pages)} | bytes={OUT.stat().st_size}')


