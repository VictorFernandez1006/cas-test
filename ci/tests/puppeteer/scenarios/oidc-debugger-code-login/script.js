const puppeteer = require('puppeteer');
const cas = require('../../cas.js');

(async () => {

    console.log("Refreshing application context...")
    let response = await cas.doRequest("https://localhost:8443/cas/actuator/refresh", "POST");
    console.log(response)
    
    const browser = await puppeteer.launch(cas.browserOptions());
    const page = await cas.newPage(browser);
    const url = "https://localhost:8443/cas/oidc/oidcAuthorize?" +
        "client_id=client&" +
        "redirect_uri=https%3A%2F%2Foidcdebugger.com%2Fdebug&" +
        "scope=openid%20email%20profile%20address%20phone&" +
        "response_type=code&" +
        "response_mode=form_post&" +
        "nonce=vn4qulthnx";
    await page.goto(url);

    await cas.loginWith(page, "casuser", "Mellon");
    await page.waitForTimeout(1000)

    await cas.click(page, "#allow");
    await page.waitForNavigation();
    await page.waitForTimeout(1000)
    await cas.assertTextContent(page, "h1.green-text", "Success!");

    await browser.close();
})();

