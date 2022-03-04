module Fastlane
  module Actions
    module SharedValues
      UPDATE_APP_TO_BUGLY_DOWNLOAD_URL = :UPDATE_APP_TO_BUGLY_DOWNLOAD_URL
    end

    # To share this integration with the other fastlane users:
    # - Fork https://github.com/fastlane/fastlane/tree/master/fastlane
    # - Clone the forked repository
    # - Move this integration into lib/fastlane/actions
    # - Commit, push and submit the pull request

    class UpdateAppToBuglyAction < Action
      def self.run(params)
        require 'json'
        UI.message "file path: #{params[:file_path]}"
        json_file = 'update_app_to_bugly_result.json'

        begin
          secret = ''
          if !params[:secret].nil? && !params[:secret].empty?
            secret = " -F \"secret=#{params[:secret]}\" "
            UI.message "secret:#{secret}"
          end
        rescue Exception => e
          UI.message "error at checking secret,caused by #{e}"
          return
        end

        begin
          title = ''
          if !params[:title].nil? && !params[:title].to_s.empty?
            title = " -F \"title=#{params[:title]}\" "
            UI.message "title:#{title}"
          end
        rescue Exception => e
          UI.message "error at checking title,caused by #{e}"
          return
        end

        begin
          desc = ''
          if !params[:desc].nil? && !params[:desc].to_s.empty?
            desc = " -F \"description=#{params[:desc]}\" "
            UI.message "desc:#{desc}"
          end
        rescue Exception => e
          UI.message "error at checking desc,caused by #{e}"
          return
        end

        begin
          users = ''
          if !params[:users].nil? && !params[:users].empty?
            users = " -F \"users=#{params[:users]}\" "
            UI.message "users:#{users}"
          end
        rescue Exception => e
          UI.message "error at checking users,caused by #{e}"
          return
        end

        begin
          password = ''
          if !params[:password].nil? && !params[:password].empty?
            password = " -F \"password=#{params[:password]}\" "
            UI.message "password:#{password}"
          end
        rescue Exception => e
          UI.message "error at checking password,caused by #{e}"
          return
        end

        begin
          download_limit = ''
          if !params[:download_limit].nil? && params[:download_limit] > 0
            download_limit = " -F \"download_limit=#{params[:download_limit]}\" "
            UI.message "download_limit:#{download_limit}"
          end
        rescue Exception => e
          UI.message "error at checking download_limit,caused by #{e}"
          return
        end
        
        cmd = "curl --insecure -X \"PUT\" -F \"file=@#{params[:file_path]}\" -F \"exp_id=#{params[:exp_id]}\"" + title + desc + secret + users + password + download_limit + "https://api.bugly.qq.com/beta/apiv1/exp?app_key=#{params[:app_key]} -o " + json_file
        sh(cmd)
        obj = JSON.parse(File.read(json_file))
        ret = obj['rtcode']
        if ret == 0
          UI.message "update success"
          url = obj['data']['url']
          Actions.lane_context[SharedValues::UPDATE_APP_TO_BUGLY_DOWNLOAD_URL] = url
        else
          UI.message "update failed,result is #{obj}"
        end
      end

      #####################################################
      # @!group Documentation
      #####################################################

      def self.description
        'A short description with <= 80 characters of what this action does'
      end

      def self.details
        # Optional:
        # this is your chance to provide a more detailed description of this action
        'You can use this action to do cool things...'
      end

      def self.available_options
        # Define all options your action supports.
        # Below a few examples
        [
          FastlaneCore::ConfigItem.new(key: :file_path,
                                       env_name: 'FL_UPLOAD_APP_TO_BUGLY_FILE_PATH',
                                       description: 'file path for UploadAppToBuglyAction',
                                       is_string: true,
                                       verify_block: proc do |value|
                                         UI.user_error!("No file path for UploadAppToBuglyAction given, pass using `file_path: 'path'`") unless value && !value.empty?
                                         UI.user_error!("Couldn't find file at path '#{value}'") unless File.exist?(value)
                                       end),
          FastlaneCore::ConfigItem.new(key: :app_key,
                                       env_name: 'FL_UPLOAD_APP_TO_BUGLY_APP_KEY',
                                       description: 'app key for UploadAppToBuglyAction',
                                       is_string: true,
                                       verify_block: proc do |value|
                                         UI.user_error!("NO app_key for UploadAppToBuglyAction given, pass using `app_key: 'app_key'`") unless value && !value.empty?
                                       end),
          FastlaneCore::ConfigItem.new(key: :exp_id,
                                       env_name: 'FL_UPLOAD_APP_TO_BUGLY_EXP_ID',
                                       description: 'exp id for UploadAppToBuglyAction',
                                       is_string: true,
                                       verify_block: proc do |value|
                                         UI.user_error!("No exp_id for UploadAppToBuglyAction given, pass using `exp_id: 'exp_id'`") unless value && !value.empty?
                                       end),
          FastlaneCore::ConfigItem.new(key: :title,
                                       env_name: 'FL_UPLOAD_APP_TO_BUGLY_TITLE',
                                       description: 'title for UploadAppToBuglyAction',
                                       is_string: true,
                                       default_value: ''),
          FastlaneCore::ConfigItem.new(key: :desc,
                                       env_name: 'FL_UPLOAD_APP_TO_BUGLY_DESC',
                                       description: 'desc for UploadAppToBuglyAction',
                                       is_string: true,
                                       default_value: 'description'),
          FastlaneCore::ConfigItem.new(key: :secret,
                                       env_name: 'FL_UPLOAD_APP_TO_BUGLY_DESCRIPTION',
                                       description: 'secret for UploadAppToBuglyAction',
                                       is_string: true,
                                       default_value: ''),
          FastlaneCore::ConfigItem.new(key: :users,
                                       env_name: 'FL_UPLOAD_APP_TO_BUGLY_DESCRIPTION',
                                       description: 'users for UploadAppToBuglyAction',
                                       is_string: true,
                                       default_value: ''),
          FastlaneCore::ConfigItem.new(key: :password,
                                       env_name: 'FL_UPLOAD_APP_TO_BUGLY_DESCRIPTION',
                                       description: 'password for UploadAppToBuglyAction',
                                       is_string: true,
                                       default_value: ''),
          FastlaneCore::ConfigItem.new(key: :download_limit,
                                       env_name: 'FL_UPLOAD_APP_TO_BUGLY_DESCRIPTION',
                                       description: 'download_limit for UploadAppToBuglyAction',
                                       is_string: false,
                                       default_value: 10_000)
        ]
      end

      def self.output
        # Define the shared values you are going to provide
        # Example
        [
          ['UPDATE_APP_TO_BUGLY_DOWNLOAD_URL', 'download url']
        ]
      end

      def self.return_value
        # If you method provides a return value, you can describe here what it does
      end

      def self.authors
        # So no one will ever forget your contribution to fastlane :) You are awesome btw!
        ["panweiji"]
      end

      def self.is_supported?(platform)
        # you can do things like
        #
        #  true
        #
        #  platform == :ios
        #
        #  [:ios, :mac].include?(platform)
        #

        platform == :ios
      end
    end
  end
end
