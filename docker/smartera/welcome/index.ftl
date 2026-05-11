<!DOCTYPE html>
<html>
<head>
    <title>Welcome to ${productNameFull}</title>

    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="robots" content="noindex, nofollow">

    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
</head>

<body>
<div class="container-fluid">
  <div class="row">
    <div class="col-sm-10 col-sm-offset-1 col-md-8 col-md-offset-2 col-lg-8 col-lg-offset-2">
      <div class="welcome-header">
        <img src="${resourcesPath}/smartera-logo.png" alt="${productName}" border="0" />
        <h1>Welcome to <strong>${productNameFull}</strong> SmartEra Identity Manager</h1>
      </div>
      <div class="row">
        <div class="col-xs-12 col-sm-4">
          <div class="card-pf h-l">
            <#if successMessage?has_content>
                <p class="alert success">${successMessage}</p>
            <#elseif errorMessage?has_content>
                <p class="alert error">${errorMessage}</p>
            </#if>
            <div class="welcome-primary-link">
              <h3><a href="${adminUrl}"><img src="${resourcesPath}/user.png"> Administration Console</a></h3>
              <div class="description">
                Centrally manage all aspects of the ${productNameFull} server
              </div>
            </div>
          </div>
        </div>
        <div class="col-xs-12 col-sm-4">
          <div class="card-pf h-l">
            <h3><a href="${properties.documentationUrl!'https://www.keycloak.org/documentation.html'}"><img class="doc-img" src="${resourcesPath}/admin-console.png"> Documentation</a></h3>
            <div class="description">
              User Guide, Admin REST API and Javadocs
            </div>
          </div>
        </div>
        <#if properties.displayCommunityLinks = "true">
        <div class="col-xs-12 col-sm-4">
          <div class="card-pf h-m">
            <h3><a href="http://www.keycloak.org"><img src="${resourcesPath}/keycloak-project.png"> Keycloak Project</a></h3>
          </div>
          <div class="card-pf h-m">
            <h3><a href="https://groups.google.com/forum/#!forum/keycloak-user"><img src="${resourcesPath}/mail.png"> Mailing List</a></h3>
          </div>
          <div class="card-pf h-m">
            <h3><a href="https://issues.jboss.org/browse/KEYCLOAK"><img src="${resourcesPath}/bug.png"> Report an issue</a></h3>
          </div>
        </div>
        </#if>
      </div>
      <div class="footer">
        <#if properties.displayCommunityLinks = "true">
        <a href="http://www.jboss.org"><img src="${resourcesPath}/jboss_community.png" alt="JBoss and JBoss Community"></a>
        </#if>
      </div>
    </div>
  </div>
</div>
</body>
</html>
