<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:template match="/">
        <html>
            <head>
                <title>Test Report Summary</title>
                <style>
                    body { font-family: sans-serif; margin: 20px; }
                    h2 { color: #333; }
                    table { border-collapse: collapse; width: 100%; margin-bottom: 20px; }
                    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                    th { background-color: #f2f2f2; }
                    .pass { color: green; font-weight: bold; }
                    .fail { color: red; font-weight: bold; }
                    .summary-box { background-color: #e8f4f8; padding: 15px; border-left: 5px solid #2980b9; margin-bottom: 20px; }
                    details { margin-bottom: 10px; border: 1px solid #ddd; padding: 5px; }
                    summary { font-weight: bold; cursor: pointer; color: #555; }
                    pre { background-color: #f9f9f9; padding: 10px; overflow: auto; max-height: 300px; font-size: 0.9em; color: #666; }
                </style>
            </head>
            <body>
                <h2>Test Suite Report</h2>
                
                <!-- Summary Property Box -->
                <div class="summary-box">
                    <h3>SFR Verification Summary</h3>
                    <p>
                        <xsl:value-of select="testsuite/properties/property[@name='summary']/@value"/>
                    </p>
                </div>

                <h3>Properties</h3>
                <table>
                    <tr><th>Name</th><th>Value</th></tr>
                    <xsl:for-each select="testsuite/properties/property">
                        <tr>
                            <td><xsl:value-of select="@name"/></td>
                            <td><xsl:value-of select="@value"/></td>
                        </tr>
                    </xsl:for-each>
                </table>

                <h3>Test Cases</h3>
                <table>
                    <tr><th>Class Name</th><th>Method Name</th><th>Status</th></tr>
                    <xsl:for-each select="testsuite/testcase">
                        <tr>
                            <td><xsl:value-of select="@classname"/></td>
                            <td><xsl:value-of select="@name"/></td>
                            <td>
                                <xsl:choose>
                                    <xsl:when test="failure">
                                        <span class="fail">FAIL</span>
                                    </xsl:when>
                                    <xsl:when test="error">
                                        <span class="fail">ERROR</span>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <span class="pass">PASS</span>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </td>
                        </tr>
                    </xsl:for-each>
                </table>

                <!-- Logs Section -->
                <h3>Logs</h3>
                <details>
                    <summary>View System Out Logs</summary>
                    <pre><xsl:value-of select="testsuite/system-out"/></pre>
                </details>

                <details>
                    <summary>View System Err Logs</summary>
                    <pre><xsl:value-of select="testsuite/system-err"/></pre>
                </details>
            </body>
        </html>
    </xsl:template>
</xsl:stylesheet>
