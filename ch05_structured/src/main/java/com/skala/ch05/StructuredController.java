package com.skala.ch05;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/ch05")
public class StructuredController {

    private final StructuredOutputService structured;
    private final MultimodalService multimodal;

    public StructuredController(StructuredOutputService structured, MultimodalService multimodal) {
        this.structured = structured;
        this.multimodal = multimodal;
    }

    /** curl 'localhost:8080/ch05/classify?q=카드가 두 번 결제됐어요' */
    @GetMapping("/classify")
    public StructuredOutputService.Ticket classify(@RequestParam String q) {
        return structured.classifySafely(q);
    }

    @PostMapping(value = "/keywords", consumes = "text/plain")
    public List<StructuredOutputService.Keyword> keywords(@RequestBody String text) {
        return structured.keywords(text);
    }

    @PostMapping(value = "/company", consumes = "text/plain")
    public StructuredOutputService.Company company(@RequestBody String document) {
        return structured.extractCompany(document);
    }

    /** curl -F 'file=@receipt.png' localhost:8080/ch05/receipt */
    @PostMapping("/receipt")
    public MultimodalService.ReceiptInfo receipt(@RequestPart("file") MultipartFile file) {
        return multimodal.readReceipt(file);
    }
}
