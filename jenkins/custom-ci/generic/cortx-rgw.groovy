#!/usr/bin/env groovy
pipeline {
    agent {
        node {
            label "docker-${os_version}-node"
        }
    }
    
    environment { 
        component = "csm-rgw"
        branch = "custom-ci"
        release_dir = "/mnt/bigstorage/releases/cortx"
        release_tag = "custom-build-$CUSTOM_CI_BUILD_ID"
        build_upload_dir = "$release_dir/github/integration-custom-ci/$os_version/$release_tag/cortx_iso"
    }

    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps ()
        ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '5', numToKeepStr: '10'))
    }
    
    parameters {  
        string(name: 'CORTX_RGW_URL', defaultValue: 'https://github.com/Seagate/cortx-rgw', description: 'Repository URL for cortx-rgw build')
        string(name: 'CORTX_RGW_BRANCH', defaultValue: 'main', description: 'Branch for cortx-rgw build')
        // Add os_version parameter in jenkins configuration
    }    


    stages {

        stage('Checkout cortx-rgw') {
            steps {
                script { build_stage = env.STAGE_NAME }
                dir ('cortx-rgw') {
                checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RGW_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RGW_URL}"]]])
                }
            }
        }
        
        stage('Install Dependencies') {
            steps {
                script { build_stage = env.STAGE_NAME }

                sh label: 'Build', script: '''
                pushd cortx-rgw
                    sed -i 's/centos|/rocky|centos|/' install-deps.sh
                    ./install-deps.sh
                    ./make-dist
                    tar --strip-components=1 -C /root/rpmbuild/SPECS/ --no-anchored -xvjf ceph-*tar.bz2 "ceph.spec"
                    mv ceph*tar.bz2 /root/rpmbuild/SOURCES/                    
                popd
                '''
            }
        }

        stage('Build cortx-rgw packages') {
            steps {
                script { build_stage = env.STAGE_NAME }

                sh label: 'Build', script: '''
                cd /root/rpmbuild/
                rpmbuild --clean --rmsource --define "_unpackaged_files_terminate_build 0" --define "debug_package %{nil}" --without cmake_verbose_logging --without jaeger --without lttng --without seastar --without kafka_endpoint --without zbd --without cephfs_java --without cephfs_shell --without ocf --without selinux --without ceph_test_package --without make_check --define "_binary_payload w2T16.xzdio" --define "_topdir `pwd`" -ba /root/rpmbuild/SPECS/ceph.spec
                '''
            }
        }  
    }
}