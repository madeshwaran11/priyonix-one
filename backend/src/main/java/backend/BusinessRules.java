package backend;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

/** Pure validation/calculation; neither client-supplied totals nor arbitrary JSON is persisted. */
public final class BusinessRules {
    private BusinessRules() {}
    private static final Pattern EMAIL=Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final BigDecimal LIMIT=new BigDecimal("1000000000000");
    public static Map<String,Object> normalize(WorkspaceSchema schema, Map<String,Object> input) {
        if(input==null) throw invalid("values is required");
        Set<String> allowed=new HashSet<>(); schema.fields().forEach(f->allowed.add(f.key()));
        for(String key:input.keySet()) if(!allowed.contains(key)) throw invalid("Unknown or read-only field: "+key);
        Map<String,Object> values=new LinkedHashMap<>();
        for(var field:schema.fields()) {
            Object raw=input.get(field.key());
            if(raw==null || raw instanceof String s && s.isBlank()) {
                if(field.required()) throw invalid(field.label()+" is required");
                continue;
            }
            Object value;
            if(field.type().equals("number")) {
                if(!(raw instanceof Number || raw instanceof String)) throw invalid(field.label()+" must be a number");
                try { var n=new BigDecimal(raw.toString()); if(n.abs().compareTo(LIMIT)>0 || n.scale()>6) throw invalid(field.label()+" is too large or precise"); value=n; }
                catch(NumberFormatException ex) { throw invalid(field.label()+" must be a finite number"); }
            } else {
                if(!(raw instanceof String)) throw invalid(field.label()+" must be text");
                String s=((String)raw).trim();
                if(s.length()>(field.type().equals("textarea")?5000:300) || s.indexOf('\0')>=0) throw invalid(field.label()+" is too long or invalid");
                if(field.type().equals("date")) { try { LocalDate.parse(s); } catch(Exception ex) { throw invalid(field.label()+" must be an ISO date (YYYY-MM-DD)"); } }
                if(field.type().equals("email")) { if(!EMAIL.matcher(s).matches()) throw invalid(field.label()+" is invalid"); s=s.toLowerCase(Locale.ROOT); }
                if(field.type().equals("select") && !field.options().contains(s)) throw invalid(field.label()+" must be one of "+String.join(", ",field.options()));
                value=s;
            }
            values.put(field.key(),value);
        }
        for(var e:values.entrySet()) if(e.getValue() instanceof BigDecimal n && n.signum()<0) throw invalid(e.getKey()+" cannot be negative");
        String module=schema.key();
        for(String field:List.of("quantity","hours","maximum","capacity")) {
            if(values.containsKey(field) && !(module.equals("attendance") && field.equals("hours")) && number(values,field).signum()<=0) throw invalid(field+" must be greater than zero");
        }
        if(values.containsKey("taxRate") && number(values,"taxRate").compareTo(new BigDecimal("100"))>0) throw invalid("Tax rate cannot exceed 100%");
        if((module.equals("timesheets")||module.equals("attendance")) && number(values,"hours").compareTo(new BigDecimal("24"))>0) throw invalid("Hours cannot exceed 24 per day");
        if(module.equals("attendance") && "ABSENT".equals(values.get("presence")) && number(values,"hours").signum()!=0) throw invalid("Absent attendance must have zero hours");
        if(values.containsKey("startDate") && values.containsKey("endDate")) requireOrder(values,"startDate","endDate");
        if(module.equals("purchase-orders")) requireOrder(values,"orderDate","expectedDate");
        if(module.equals("leave")) {
            long days=ChronoUnit.DAYS.between(day(values,"startDate"),day(values,"endDate"))+1;
            if(days>366) throw invalid("A leave request cannot exceed 366 calendar days"); values.put("days",days);
        }
        if(module.equals("quotations") || module.equals("purchase-orders")) {
            var subtotal=number(values,"quantity").multiply(number(values,"unitPrice")).setScale(2,RoundingMode.HALF_UP);
            var tax=subtotal.multiply(number(values,"taxRate")).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
            values.put("subtotal",subtotal); values.put("tax",tax); values.put("total",subtotal.add(tax)); values.put("currency","INR");
        }
        if(module.equals("assessments")) {
            if(number(values,"score").compareTo(number(values,"maximum"))>0) throw invalid("Score cannot exceed the maximum");
            values.put("percentage",number(values,"score").multiply(new BigDecimal("100")).divide(number(values,"maximum"),2,RoundingMode.HALF_UP));
        }
        if(values.containsKey("capacity") && number(values,"capacity").stripTrailingZeros().scale()>0) throw invalid("Capacity must be a whole number");
        return values;
    }
    public static BigDecimal number(Map<String,Object> v,String key) { Object value=v.get(key); return value==null?BigDecimal.ZERO:new BigDecimal(value.toString()); }
    public static LocalDate day(Map<String,Object> v,String key) { return LocalDate.parse(Objects.toString(v.get(key))); }
    private static void requireOrder(Map<String,Object> v,String a,String b) { if(day(v,b).isBefore(day(v,a))) throw invalid(b+" cannot be before "+a); }
    private static InvalidRequestException invalid(String message) { return new InvalidRequestException(message); }
}
