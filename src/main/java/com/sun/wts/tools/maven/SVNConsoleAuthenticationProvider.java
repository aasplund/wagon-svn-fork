/*
The TMate License

This license applies to all portions of TMate SVNKit library, which
are not externally-maintained libraries (e.g. Ganymed SSH library).

All the source code and compiled classes in package org.tigris.subversion.javahl
except SvnClient class are covered by the license in JAVAHL-LICENSE file

Copyright (c) 2004-2007 TMate Software. All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above copyright notice,
      this list of conditions and the following disclaimer in the documentation
      and/or other materials provided with the distribution.

    * Redistributions in any form must be accompanied by information on how to
      obtain complete source code for the software that uses SVNKit and any
      accompanying software that uses the software that uses SVNKit. The source
      code must either be included in the distribution or be available for no
      more than the cost of distribution plus a nominal fee, and must be freely
      redistributable under reasonable conditions. For an executable file, complete
      source code means the source code for all modules it contains. It does not
      include source code for modules or files that typically accompany the major
      components of the operating system on which the executable file runs.

    * Redistribution in any form without redistributing source code for software
      that uses SVNKit is possible only when such redistribution is explictly permitted
      by TMate Software. Please, contact TMate Software at support@svnkit.com to
      get such permission.

THIS SOFTWARE IS PROVIDED BY TMATE SOFTWARE ``AS IS'' AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, OR NON-INFRINGEMENT, ARE
DISCLAIMED.

IN NO EVENT SHALL TMATE SOFTWARE BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.sun.wts.tools.maven;

import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.cert.X509Certificate;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNConsoleAuthenticationProvider implements ISVNAuthenticationProvider {

    private final boolean isInteractive;

    private final AuthenticationInfo auth;

    public SVNConsoleAuthenticationProvider(boolean interactive, AuthenticationInfo auth) {
        this.isInteractive = interactive;
        this.auth = auth;
    }

    public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
        if (!(certificate instanceof X509Certificate) || !isInteractive) {
            return ACCEPTED_TEMPORARY;
        }

        String hostName = url.getHost();
        X509Certificate cert = (X509Certificate) certificate;
        StringBuffer prompt = SVNSSLUtil.getServerCertificatePrompt(cert, realm, hostName);
        if (resultMayBeStored) {
            prompt.append("\n(R)eject, accept (t)emporarily or accept (p)ermanently? ");
        } else {
            prompt.append("\n(R)eject or accept (t)emporarily? ");
        }
        System.out.print(prompt.toString());
        System.out.flush();
        int r;
        while(true) {
            try {
                r = System.in.read();
                if (r < 0) {
                    return REJECTED;
                }
                char ch = (char) (r & 0xFF);
                if (ch == 'R' || ch == 'r') {
                    return REJECTED;
                } else if (ch == 't' || ch == 'T') {
                    return ACCEPTED_TEMPORARY;
                } else if (resultMayBeStored && (ch == 'p' || ch == 'P')) {
                    return ACCEPTED;
                }
            } catch (IOException e) {
                return REJECTED;
            }
        }
    }

    public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
        if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
            if(auth!=null && auth.getPassword()!=null)
                return new SVNPasswordAuthentication(auth.getUserName(),auth.getPassword(),authMayBeStored);

            String name = null;
            printRealm(realm);
            while(name == null) {
                name = prompt("Username");
                if ("".equals(name)) {
                    name = null;
                }
            }
            String password = prompt("Password for '" + name + "'");
            if (password == null) {
                password = "";
            }
            return new SVNPasswordAuthentication(name, password, authMayBeStored);
        } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
            if(auth!=null) {
                if(auth.getPassword()!=null)
                    return new SVNSSHAuthentication(auth.getUserName(),auth.getPassword(),22,authMayBeStored);
                if(auth.getPrivateKey()!=null)
                    return new SVNSSHAuthentication(auth.getUserName(),new File(auth.getPrivateKey()),auth.getPassphrase(),22,authMayBeStored);
            }

            String name = null;
            printRealm(realm);
            while(name == null) {
                name = prompt("Username");
                if ("".equals(name)) {
                    name = null;
                }
            }
            String password = prompt("Password for '" + url.getHost() + "' (leave blank if you are going to use private key)");
            if ("".equals(password)) {
                password = null;
            }
            String keyFile = null;
            String passphrase = null;
            if (password == null) {
                while(keyFile == null) {
                    keyFile = prompt("Private key for '" + url.getHost() + "' (OpenSSH format)");
                    if ("".equals(keyFile)) {
                        name = null;
                    }
                    File file = new File(keyFile);
                    if (!file.isFile() && !file.canRead()) {
                        continue;
                    }
                    passphrase = prompt("Private key passphrase [none]");
                    if ("".equals(passphrase)) {
                        passphrase = null;
                    }
                }
            }
            int port = 22;
            String portValue = prompt("Port number for '" + url.getHost() + "' [22]");
            if (portValue != null && !"".equals(portValue)) {
                try {
                    port = Integer.parseInt(portValue);
                } catch (NumberFormatException e) {}
            }
            if (password != null) {
                return new SVNSSHAuthentication(name, password, port, authMayBeStored);
            } else if (keyFile != null) {
                return new SVNSSHAuthentication(name, new File(keyFile), passphrase, port, authMayBeStored);
            }
        } else if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
            if(auth!=null && auth.getUserName()!=null)
                return new SVNUserNameAuthentication(auth.getUserName(),authMayBeStored);

            printRealm(realm);
            String name = null;
            while(name == null) {
                name = prompt(!"file".equals(url.getProtocol()) ?
                    "Author name [" + System.getProperty("user.name") + "]" :
                    "Username [" + System.getProperty("user.name") + "]");
                if ("".equals(name) || name == null) {
                    name = System.getProperty("user.name");
                }
            }
            return new SVNUserNameAuthentication(name, authMayBeStored);
        } else if (ISVNAuthenticationManager.SSL.equals(kind)) {
            printRealm(realm);
            String path = null;
            while(path == null) {
                path = prompt("Client certificate filename");
                if ("".equals(path)) {
                    path = null;
                }
            }
            String password = prompt("Passphrase for '" + realm + "'");
            if (password == null) {
                password = "";
            }
            return new SVNSSLAuthentication(new File(path), password, authMayBeStored);
        }
        return null;
    }

    private static void printRealm(String realm) {
        if (realm != null) {
            System.out.println("Authentication realm: " + realm);
            System.out.flush();
        }
    }

    private String prompt(String label) {
        if(!isInteractive)  return null;

        System.out.print(label + ": ");
        System.out.flush();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            return reader.readLine();
        } catch (IOException e) {
            return null;
        }
    }
}
