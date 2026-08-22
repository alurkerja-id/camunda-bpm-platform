<!DOCTYPE html>

<html>

<head>

<meta http-equiv="Content-Type" content="text/html; charset=utf-8">

{{- if . }}

<style>

* {
    font-family: Arial, Helvetica, sans-serif;
}

body {
    margin: 20px;
}

h1 {
    text-align: center;
}

.report-date {
    text-align: center;
    color: #666;
    margin-bottom: 25px;
}

.summary {
    margin: 0 auto 30px auto;
    min-width: 600px;
}

.summary th {
    font-size: 150%;
    padding: 10px;
}

.summary td {
    padding: 8px 15px;
}

.summary td:first-child {
    font-weight: bold;
    text-align: left;
}

.summary td:last-child {
    text-align: center;
    font-weight: bold;
}

.summary-total {
    font-size: 120%;
}

.group-header th {
    font-size: 150%;
    padding: 10px;
    background-color: #eeeeee;
}

.sub-header th {
    font-size: 120%;
}

table,
th,
td {
    border: 1px solid black;
    border-collapse: collapse;
    white-space: nowrap;
    padding: .3em;
}

table {
    margin: 0 auto 25px auto;
}

.severity {
    text-align: center;
    font-weight: bold;
    color: #fafafa;
}

.severity-LOW .severity {
    background-color: #5fbb31;
}

.severity-MEDIUM .severity {
    background-color: #e9c600;
}

.severity-HIGH .severity {
    background-color: #ff8800;
}

.severity-CRITICAL .severity {
    background-color: #e40000;
}

.severity-UNKNOWN .severity {
    background-color: #747474;
}

.severity-LOW {
    background-color: #5fbb3160;
}

.severity-MEDIUM {
    background-color: #e9c60060;
}

.severity-HIGH {
    background-color: #ff880060;
}

.severity-CRITICAL {
    background-color: #e4000060;
}

.severity-UNKNOWN {
    background-color: #74747460;
}

table tr td:first-of-type {
    font-weight: bold;
}

.links a,
.links[data-more-links=on] a {
    display: block;
}

.links[data-more-links=off] a:nth-of-type(1n+5) {
    display: none;
}

a.toggle-more-links {
    cursor: pointer;
}

</style>

<!--
    ============================================================
    SUMMARY CALCULATION
    ============================================================
-->

{{- $total := 0 }}
{{- $fixed := 0 }}
{{- $critical := 0 }}
{{- $high := 0 }}
{{- $medium := 0 }}
{{- $low := 0 }}
{{- $unknown := 0 }}

{{- $unique := dict }}

{{- range . }}

    {{- range .Vulnerabilities }}

        {{- $total = add $total 1 }}

        {{- $_ := set $unique .VulnerabilityID true }}

        {{- if .FixedVersion }}
            {{- $fixed = add $fixed 1 }}
        {{- end }}

        {{- if eq .Vulnerability.Severity "CRITICAL" }}
            {{- $critical = add $critical 1 }}

        {{- else if eq .Vulnerability.Severity "HIGH" }}
            {{- $high = add $high 1 }}

        {{- else if eq .Vulnerability.Severity "MEDIUM" }}
            {{- $medium = add $medium 1 }}

        {{- else if eq .Vulnerability.Severity "LOW" }}
            {{- $low = add $low 1 }}

        {{- else if eq .Vulnerability.Severity "UNKNOWN" }}
            {{- $unknown = add $unknown 1 }}

        {{- end }}

    {{- end }}

{{- end }}

<title>
    Trivy Report - {{ now }}
</title>

<script>

window.onload = function() {

    document.querySelectorAll('td.links').forEach(function(linkCell) {

        var links = [].concat.apply(
            [],
            linkCell.querySelectorAll('a')
        );

        [].sort.apply(links, function(a, b) {
            return a.href > b.href ? 1 : -1;
        });

        links.forEach(function(link, idx) {

            if (links.length > 3 && 3 === idx) {

                var toggleLink = document.createElement('a');

                toggleLink.innerText = "Toggle more links";

                toggleLink.href = "#toggleMore";

                toggleLink.setAttribute(
                    "class",
                    "toggle-more-links"
                );

                linkCell.appendChild(toggleLink);
            }

            linkCell.appendChild(link);
        });
    });

    document
        .querySelectorAll('a.toggle-more-links')
        .forEach(function(toggleLink) {

            toggleLink.onclick = function() {

                var expanded =
                    toggleLink.parentElement
                        .getAttribute("data-more-links");

                toggleLink.parentElement
                    .setAttribute(
                        "data-more-links",
                        "on" === expanded ? "off" : "on"
                    );

                return false;
            };

        });

};

</script>

</head>

<body>

<!-- ============================================================
     REPORT TITLE
     ============================================================ -->

<h1>
    Trivy Vulnerability Report
</h1>

<div class="report-date">
    Generated: {{ now }}
</div>


<!-- ============================================================
     REPORT SUMMARY
     ============================================================ -->

<table class="summary">

    <tr>
        <th colspan="2">
            Report Summary
        </th>
    </tr>

    <tr>
        <td class="summary-total">
            Total Findings
        </td>
        <td class="summary-total">
            {{ $total }}
        </td>
    </tr>

    <tr>
        <td>
            Unique CVEs
        </td>
        <td>
            {{ len $unique }}
        </td>
    </tr>

    <tr>
        <td>
            Fix Available
        </td>
        <td>
            {{ $fixed }}
        </td>
    </tr>

    <tr>
        <td>
            CRITICAL
        </td>
        <td>
            {{ $critical }}
        </td>
    </tr>

    <tr>
        <td>
            HIGH
        </td>
        <td>
            {{ $high }}
        </td>
    </tr>

    <tr>
        <td>
            MEDIUM
        </td>
        <td>
            {{ $medium }}
        </td>
    </tr>

    <tr>
        <td>
            LOW
        </td>
        <td>
            {{ $low }}
        </td>
    </tr>

    <tr>
        <td>
            UNKNOWN
        </td>
        <td>
            {{ $unknown }}
        </td>
    </tr>

</table>


<!-- ============================================================
     VULNERABILITY DETAILS
     ============================================================ -->

<table>

{{- range . }}

    <!-- Module / POM -->

    <tr class="group-header">

        <th colspan="6">
            {{ escapeXML .Target }}
        </th>

    </tr>


    {{- if (eq (len .Vulnerabilities) 0) }}

        <tr>
            <th colspan="6">
                No Vulnerabilities found
            </th>
        </tr>

    {{- else }}

        <tr class="sub-header">

            <th>
                Package
            </th>

            <th>
                Vulnerability ID
            </th>

            <th>
                Severity
            </th>

            <th>
                Installed Version
            </th>

            <th>
                Fixed Version
            </th>

            <th>
                Links
            </th>

        </tr>


        {{- range .Vulnerabilities }}

        <tr class="severity-{{ escapeXML .Vulnerability.Severity }}">

            <td class="pkg-name">
                {{ escapeXML .PkgName }}
            </td>

            <td>
                {{ escapeXML .VulnerabilityID }}
            </td>

            <td class="severity">
                {{ escapeXML .Vulnerability.Severity }}
            </td>

            <td class="pkg-version">
                {{ escapeXML .InstalledVersion }}
            </td>

            <td>
                {{ escapeXML .FixedVersion }}
            </td>

            <td
                class="links"
                data-more-links="off"
            >

                {{- range .Vulnerability.References }}

                    <a href={{ escapeXML . | printf "%q" }}>
                        {{ escapeXML . }}
                    </a>

                {{- end }}

            </td>

        </tr>

        {{- end }}

    {{- end }}


    <!-- ========================================================
         MISCONFIGURATIONS
         ======================================================== -->

    {{- if (eq (len .Misconfigurations) 0) }}

        <tr>
            <th colspan="6">
                No Misconfigurations found
            </th>
        </tr>

    {{- else }}

        <tr class="sub-header">

            <th>
                Type
            </th>

            <th>
                Misconf ID
            </th>

            <th>
                Check
            </th>

            <th>
                Severity
            </th>

            <th>
                Message
            </th>

        </tr>


        {{- range .Misconfigurations }}

        <tr class="severity-{{ escapeXML .Severity }}">

            <td class="misconf-type">
                {{ escapeXML .Type }}
            </td>

            <td>
                {{ escapeXML .ID }}
            </td>

            <td class="misconf-check">
                {{ escapeXML .Title }}
            </td>

            <td class="severity">
                {{ escapeXML .Severity }}
            </td>

            <td
                class="link"
                data-more-links="off"
                style="white-space:normal;"
            >

                {{ escapeXML .Message }}

                <br>

                <a href={{ escapeXML .PrimaryURL | printf "%q" }}>
                    {{ escapeXML .PrimaryURL }}
                </a>

                </br>

            </td>

        </tr>

        {{- end }}

    {{- end }}

{{- end }}

</table>


{{- else }}

<h1>
    Trivy Returned Empty Report
</h1>

{{- end }}

</body>

</html>