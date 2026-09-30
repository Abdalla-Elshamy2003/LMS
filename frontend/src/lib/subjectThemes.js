import { Atom, BookOpen, Brain, Compass, Feather, FlaskConical, Globe, Hourglass, Landmark, Lightbulb, Map as MapIcon, PenTool, ScrollText, Target, TestTube } from 'lucide-react'

/**
 * What a teacher's public page looks like for each subject: the three-point method strip under the hero,
 * the notation that drifts behind it and scrolls in the marquee, and the big glyph on the closing banner.
 * The key is stored on the academy (`subjectTheme`, validated server-side against the same list).
 *
 * `words` marks themes whose notation is Arabic text rather than formulas — those drop the italic Georgia
 * face, which would fake-slant Arabic letters.
 */
export const SUBJECT_THEMES = {
  math: {
    label: 'رياضيات',
    strip: [[BookOpen, 'شرح يبسّط الصعب'], [PenTool, 'تطبيق يثبّت الفكرة'], [Target, 'مراجعة تربط المنهج']],
    cta: 'ابدأ رحلتك',
    symbols: ['a² + b² = c²', 'ƒ(x) = 2x + 3', '∫ x dx', 'π ≈ 3.14159', '∑ⁿ', 'lim x→0', 'd/dx (x²) = 2x', '√x', 'sin²θ + cos²θ = 1', 'n!', 'Δ = b² − 4ac', 'y = mx + b'],
    floats: ['𝑥² + 𝑦²', 'π', '√𝑥'],
    glyph: '∑',
    closing: 'مسألتك الجاية.. إنت قدّها.',
    headline: 'الرياضيات مش صعبة.. محتاجة تتفهم صح.',
  },
  physics: {
    label: 'فيزياء',
    strip: [[Atom, 'قوانين بنشوفها حوالينا'], [Lightbulb, 'تجربة تقرّب الفكرة'], [Target, 'مسائل تدرّبك صح']],
    cta: 'ابدأ رحلتك',
    symbols: ['F = ma', 'E = mc²', 'V = IR', 'v = d / t', 'KE = ½mv²', 'P = W / t', 'g ≈ 9.8 m/s²', 'λ = v / f', 'ρ = m / V', 'F = kx', 'Q = mcΔT', 'ω = 2πf'],
    floats: ['F = ma', 'λ', 'E = mc²'],
    glyph: 'Ω',
    closing: 'القانون الجاي.. هتفهمه من أول مرة.',
    headline: 'الفيزياء مش قوانين تتحفظ.. دي الدنيا اللي حواليك بتتفهم.',
  },
  chemistry: {
    label: 'كيمياء',
    strip: [[FlaskConical, 'التفاعل بيتفهم خطوة بخطوة'], [TestTube, 'تجربة تثبّت المعلومة'], [Atom, 'عناصر مربوطة بالمنهج']],
    cta: 'ابدأ رحلتك',
    symbols: ['H₂O', 'NaCl', 'CO₂', '2H₂ + O₂ → 2H₂O', 'pH = 7', 'CH₄', 'NH₃', 'H₂SO₄', 'Fe³⁺', 'C₆H₁₂O₆', 'n = m / M', 'e⁻'],
    floats: ['H₂O', 'pH', 'CO₂'],
    glyph: 'Au',
    closing: 'المعادلة الجاية.. هتوزنها بنفسك.',
    headline: 'الكيمياء مش رموز وخلاص.. دي حكاية كل حاجة حواليك متكوّنة من إيه.',
  },
  history: {
    label: 'تاريخ',
    words: true,
    strip: [[ScrollText, 'حكاية تفسّر الحاضر'], [Landmark, 'أحداث مربوطة ببعض'], [Hourglass, 'مراجعة ترتّب الزمن']],
    cta: 'ابدأ رحلتك',
    symbols: ['توحيد القطرين', '٣١٠٠ ق.م', 'الفتح الإسلامي ٦٤١ م', 'الحملة الفرنسية ١٧٩٨', 'محمد علي ١٨٠٥', 'قناة السويس ١٨٦٩', 'ثورة ١٩١٩', 'ثورة ١٩٥٢', 'العدوان الثلاثي ١٩٥٦', 'نصر أكتوبر ١٩٧٣', 'الدولة الحديثة', 'حجر رشيد'],
    floats: ['١٩١٩', 'ق.م', '١٩٧٣'],
    glyph: 'ق.م',
    closing: 'الحدث الجاي.. هتعرف حكايته كاملة.',
    headline: 'التاريخ مش مجرد تواريخ وأحداث.. التاريخ حكاية بتفسّرلك الحاضر وتفهّمك اللي حصل قبلك.',
  },
  geography: {
    label: 'جغرافيا',
    words: true,
    strip: [[Globe, 'خريطة تقرّب الفكرة'], [Compass, 'نفهم الظاهرة وسببها'], [MapIcon, 'مراجعة تربط المنهج']],
    cta: 'ابدأ رحلتك',
    symbols: ['30° N', '31° E', 'نهر النيل', 'خط الاستواء', 'مدار السرطان', '1 : 50,000', 'الدلتا', 'مناخ البحر المتوسط', 'الكثافة السكانية', 'الرياح الموسمية', 'N ↑', 'الموارد الطبيعية'],
    floats: ['N ↑', '30° N', '°C'],
    glyph: '°',
    closing: 'الخريطة الجاية.. هتقراها زي كتاب مفتوح.',
    headline: 'الجغرافيا مش أسماء أماكن تتحفظ.. دي إزاي الأرض بتأثر فينا وإحنا بنأثر فيها.',
  },
  arabic: {
    label: 'لغة عربية',
    words: true,
    strip: [[Feather, 'نحو وبلاغة ببساطة'], [BookOpen, 'نصوص نفهمها ونتذوّقها'], [PenTool, 'تعبير يطلّع أحسن ما فيك']],
    cta: 'ابدأ رحلتك',
    symbols: ['مبتدأ وخبر', 'كان وأخواتها', 'إنّ وأخواتها', 'الفاعل', 'المفعول به', 'الحال', 'التمييز', 'التشبيه', 'الاستعارة', 'الجناس', 'الممنوع من الصرف', 'بحر الطويل'],
    floats: ['ض', 'ع', 'ن'],
    glyph: 'ض',
    closing: 'الإعراب الجاي.. هتقوله بثقة.',
    headline: 'العربي مش قواعد تتحفظ.. دي لغتك، ولما تفهمها هتحبها.',
  },
  philosophy: {
    label: 'فلسفة ومنطق',
    words: true,
    strip: [[Brain, 'أسئلة تفتح التفكير'], [Lightbulb, 'منطق يرتّب الفكرة'], [Target, 'مراجعة تربط المنهج']],
    cta: 'ابدأ رحلتك',
    symbols: ['سقراط', 'أفلاطون', 'أرسطو', 'الفارابي', 'ابن رشد', 'ديكارت', 'كانط', 'أنا أفكر إذن أنا موجود', 'القياس', 'الاستقراء', 'p → q', 'المعرفة'],
    floats: ['؟', '∴', 'p → q'],
    glyph: '؟',
    closing: 'السؤال الجاي.. هتفكر فيه بطريقتك.',
    headline: 'الفلسفة مش كلام صعب.. دي إزاي تسأل صح وتفكّر بعقلك.',
  },
}

export const themeFor = (key) => SUBJECT_THEMES[key] || SUBJECT_THEMES.math
